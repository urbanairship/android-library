/*
Copyright 2009-2015 Urban Airship Inc. All rights reserved.

Redistribution and use in source and binary forms, with or without
modification, are permitted provided that the following conditions are met:

1. Redistributions of source code must retain the above copyright notice, this
list of conditions and the following disclaimer.

2. Redistributions in binary form must reproduce the above copyright notice,
this list of conditions and the following disclaimer in the documentation
and/or other materials provided with the distribution.

THIS SOFTWARE IS PROVIDED BY THE URBAN AIRSHIP INC ``AS IS'' AND ANY EXPRESS OR
IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF
MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO
EVENT SHALL URBAN AIRSHIP INC OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT,
INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING,
BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE,
DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF
LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE
OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF
ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

package com.urbanairship.location;

import android.Manifest;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.location.Location;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.Message;
import android.os.Messenger;
import android.os.RemoteException;
import android.os.ResultReceiver;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.annotation.RequiresPermission;
import android.support.v4.content.LocalBroadcastManager;
import android.util.SparseArray;

import com.urbanairship.BaseManager;
import com.urbanairship.Logger;
import com.urbanairship.PendingResult;
import com.urbanairship.PreferenceDataStore;
import com.urbanairship.UAirship;
import com.urbanairship.analytics.Analytics;
import com.urbanairship.json.JsonException;

import java.util.ArrayList;
import java.util.List;

/**
 * High level interface for interacting with location.
 */
public class UALocationManager extends BaseManager {

    static final String LOCATION_UPDATES_ENABLED_KEY = "com.urbanairship.location.LOCATION_UPDATES_ENABLED";
    static final String BACKGROUND_UPDATES_ALLOWED_KEY = "com.urbanairship.location.BACKGROUND_UPDATES_ALLOWED";
    static final String LOCATION_OPTIONS_KEY = "com.urbanairship.location.LOCATION_OPTIONS";

    private final Messenger messenger;
    private final Context context;
    private Messenger serviceMessenger;

    private boolean isBound;
    private boolean isSubscribed;

    private int nextSingleLocationRequestId = 1;
    private final SparseArray<SingleLocationRequest> singleLocationRequests = new SparseArray<>();

    private final PreferenceDataStore preferenceDataStore;

    /**
     * List of location listeners.
     */
    private final List<LocationListener> locationListeners = new ArrayList<>();

    /**
     * Handles connections to the location service.
     */
    private final ServiceConnection serviceConnection = new ServiceConnection() {

        @Override
        public void onServiceConnected(ComponentName className, IBinder service) {
            Logger.verbose("Location service connected.");
            UALocationManager.this.onServiceConnected(service);
        }

        @Override
        public void onServiceDisconnected(ComponentName className) {
            Logger.verbose("Location service disconnected.");
            UALocationManager.this.onServiceDisconnected();
        }
    };

    /**
     * When preferences are changed on the current process or other processes,
     * it will trigger the PreferenceChangeListener.  Instead of dealing
     * with the changes twice (one in the set method, one here), we will
     * just deal with changes when the listener notifies the manager.
     */
    private final PreferenceDataStore.PreferenceChangeListener preferenceChangeListener = new PreferenceDataStore.PreferenceChangeListener() {
        @Override
        public void onPreferenceChange(String key) {
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
    public UALocationManager(@NonNull final Context context, @NonNull PreferenceDataStore preferenceDataStore) {
        this.context = context.getApplicationContext();
        this.preferenceDataStore = preferenceDataStore;
        this.messenger = new Messenger(new IncomingHandler(Looper.getMainLooper()));
    }

    @Override
    protected void init() {


        preferenceDataStore.addListener(preferenceChangeListener);

        /*
         * When the app is started because of a location update, takeoff is
         * called causing the UALocationManager to possibly start the location
         * service.  When this happens, the start service is on the queue before
         * the location update.  We need the location update to be processed first
         * so we can parse the last request options before starting the service.
         * The last request options allow us to determine if we can skip requesting
         * updates, preventing possible duplicate location fixes.
         *
         */
        new Handler(Looper.getMainLooper()).postDelayed(new Runnable() {
            @Override
            public void run() {

                // Set up a broadcast receiver to listen for app foreground events.
                IntentFilter filter = new IntentFilter();
                filter.addAction(Analytics.ACTION_APP_FOREGROUND);
                filter.addAction(Analytics.ACTION_APP_BACKGROUND);

                LocalBroadcastManager broadcastManager = LocalBroadcastManager.getInstance(context);
                broadcastManager.registerReceiver(new BroadcastReceiver() {
                    @Override
                    public void onReceive(Context context, Intent intent) {
                        updateServiceConnection();
                    }
                }, filter);

                updateServiceConnection();
            }
        }, 1000);
    }

    @Override
    protected void tearDown() {
        preferenceDataStore.removeListener(preferenceChangeListener);
    }


    /**
     * Checks if continuous location updates is enabled or not.
     * </p>
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
     * </p>
     * Features that depend on analytics being enabled may not work properly if it's disabled (reports,
     * region triggers, location segmentation, push to local time).
     *
     * @param enabled If location updates should be enabled or not.
     */
    @RequiresPermission(anyOf = {
            Manifest.permission.ACCESS_COARSE_LOCATION,
            Manifest.permission.ACCESS_FINE_LOCATION })
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
        LocationRequestOptions options = null;

        String jsonString = preferenceDataStore.getString(LOCATION_OPTIONS_KEY, null);
        if (jsonString != null) {
            try {
                options = LocationRequestOptions.parseJson(jsonString);
            } catch (JsonException e) {
                Logger.error("UALocationManager - Failed parsing LocationRequestOptions from JSON: " + e.getMessage());
            } catch (IllegalArgumentException e) {
                Logger.error("UALocationManager - Invalid LocationRequestOptions from JSON: " + e.getMessage());
            }
        }

        if (options == null) {
            options = new LocationRequestOptions.Builder().create();
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
        synchronized (locationListeners) {
            locationListeners.add(listener);
            updateServiceConnection();
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
            updateServiceConnection();
        }
    }

    /**
     * Records a single location using either the foreground request options
     * or the background request options depending on the application's state.
     *
     * @return A pending result for the location. The pending result may return a null location if
     * the request is unable to be made due to insufficient permissions.
     */
    @NonNull
    @RequiresPermission(anyOf = {
            Manifest.permission.ACCESS_COARSE_LOCATION,
            Manifest.permission.ACCESS_FINE_LOCATION })
    public PendingResult<Location> requestSingleLocation() {
        return requestSingleLocation(getLocationRequestOptions());
    }

    /**
     * Records a single location using custom location request options.
     *
     * @param requestOptions The location request options.
     * @return A pending result for the location. The pending result may return a null location if
     * the request is unable to be made due to insufficient permissions.
     * @throws IllegalArgumentException if the requestOptions is null.
     */
    @NonNull
    @RequiresPermission(anyOf = {
            Manifest.permission.ACCESS_COARSE_LOCATION,
            Manifest.permission.ACCESS_FINE_LOCATION })
    public PendingResult<Location> requestSingleLocation(@NonNull LocationRequestOptions requestOptions) {
        //noinspection ConstantConditions
        if (requestOptions == null) {
            throw new IllegalArgumentException("Location request options cannot be null or invalid");
        }

        SingleLocationRequest request;
        synchronized (singleLocationRequests) {
            int id = nextSingleLocationRequestId++;
            request = new SingleLocationRequest(id, requestOptions);
            singleLocationRequests.put(id, request);
        }

        synchronized (this) {
            if (!isBound) {
                bindService();
            } else {
                request.sendLocationRequest();
            }
        }

        return request;
    }

    /**
     * Updates the service connection. Handles binding and subscribing to
     * the location service.
     */
    private void updateServiceConnection() {
        ResultReceiver resultReceiver = new ResultReceiver(new Handler(Looper.getMainLooper())) {
            @Override
            protected void onReceiveResult(int resultCode, Bundle resultData) {
                if (resultCode == LocationService.RESULT_LOCATION_UPDATES_STARTED) {
                    onUpdatesStarted();
                } else {
                    onUpdatesStopped();
                }
            }

            private void onUpdatesStarted() {
                synchronized (locationListeners) {
                    if (!locationListeners.isEmpty()) {
                        if (isBound) {
                            subscribeUpdates();
                        } else {
                            // Once bound we will call updateServiceConnection again.
                            bindService();
                        }
                    }
                }
            }

            private void onUpdatesStopped() {
                unsubscribeUpdates();
                synchronized (singleLocationRequests) {
                    // unbind service
                    if (singleLocationRequests.size() == 0) {
                        unbindService();
                    }
                }
            }
        };

        Intent intent = new Intent(context, LocationService.class)
                .setAction(LocationService.ACTION_CHECK_LOCATION_UPDATES)
                .putExtra(LocationService.EXTRA_RESULT_RECEIVER, resultReceiver);

        if (context.startService(intent) == null) {
            Logger.error("Unable to start location service. Check that the location service is added to the manifest.");
        }
    }

    /**
     * Starts and binds the location service.
     */
    private synchronized void bindService() {
        if (!isBound) {
            Logger.verbose("UALocationManager - Binding to location service.");


            Context context = UAirship.getApplicationContext();
            Intent intent = new Intent(context, LocationService.class);
            if (context.bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE)) {
                isBound = true;
            } else {
                Logger.error("Unable to bind to location service. Check that the location service is added to the manifest.");
            }
        }
    }

    /**
     * Subscribes to the location service for updates.
     */
    private synchronized void subscribeUpdates() {
        if (!isSubscribed && sendMessage(LocationService.MSG_SUBSCRIBE_UPDATES, 0, null)) {
            Logger.info("Subscribing to continuous location updates.");
            isSubscribed = true;
        }
    }

    /**
     * Unsubscribes to the location service for updates.
     */
    private synchronized void unsubscribeUpdates() {
        if (isSubscribed) {
            Logger.info("Unsubscribing from continuous location updates.");
            sendMessage(LocationService.MSG_UNSUBSCRIBE_UPDATES, 0, null);
            isSubscribed = false;

            updateServiceConnection();
        }
    }

    /**
     * Unbinds the location service.
     * <p/>
     * Does not request the location service to stop, the location service will
     * stop itself on its own.
     */
    private synchronized void unbindService() {
        if (isBound) {
            Logger.verbose("UALocationManager - Unbinding to location service.");

            UAirship.getApplicationContext().unbindService(serviceConnection);
            isBound = false;
        }
    }

    private synchronized void onServiceConnected(IBinder service) {
        serviceMessenger = new Messenger(service);

        // Send any location requests that we have in flight.
        synchronized (singleLocationRequests) {
            for (int i = 0; i < singleLocationRequests.size(); i++) {
                singleLocationRequests.valueAt(i).sendLocationRequest();
            }
        }
        updateServiceConnection();
    }

    private synchronized void onServiceDisconnected() {
        serviceMessenger = null;
        isSubscribed = false;
    }

    /**
     * Helper method that constructs and sends a message to the location
     * service.  The message will be populated with the supplied what and data
     * parameters and automatically set the replyto field to the UALocationManager's
     * messenger.
     *
     * @param what The message's what field.
     * @param arg1 The message's arg1 field.
     * @param data The message's data field.
     */
    private boolean sendMessage(int what, int arg1, @Nullable Bundle data) {
        if (serviceMessenger == null) {
            return false;
        }

        Message message = Message.obtain(null, what, arg1, 0);
        if (data != null) {
            message.setData(data);
        }

        message.replyTo = messenger;

        try {
            serviceMessenger.send(message);
            return true;
        } catch (RemoteException e) {
            Logger.debug("UALocationManager - Remote exception when sending message to location service");
        }
        return false;
    }

    /**
     * Handler of incoming messages from service.
     */
    private static class IncomingHandler extends Handler {

        IncomingHandler(Looper looper) {
            super(looper);
        }

        @Override
        public void handleMessage(Message msg) {
            UALocationManager manager = UAirship.shared().getLocationManager();

            switch (msg.what) {
                case LocationService.MSG_NEW_LOCATION_UPDATE:
                    Location location = (Location) msg.obj;
                    if (location != null) {

                        // Notify the listeners of the new location
                        synchronized (manager.locationListeners) {
                            for (LocationListener listener : manager.locationListeners) {
                                listener.onLocationChanged(location);
                            }
                        }
                    }
                    break;
                case LocationService.MSG_SINGLE_REQUEST_RESULT:
                    location = (Location) msg.obj;
                    int requestId = msg.arg1;

                    // Send any location requests that we have in flight.
                    synchronized (manager.singleLocationRequests) {
                        PendingLocationResult request = manager.singleLocationRequests.get(requestId);
                        if (request != null) {
                            request.setResult(location);
                            manager.singleLocationRequests.remove(requestId);
                            manager.updateServiceConnection();
                        }
                    }
                    break;
                default:
                    super.handleMessage(msg);
            }
        }
    }

    /**
     * A request for a single location.
     */
    private class SingleLocationRequest extends PendingLocationResult {

        private final LocationRequestOptions options;
        private final int requestId;

        /**
         * SingleLocationRequest constructor.
         *
         * @param requestId The request id.
         * @param options The location request options.
         */
        SingleLocationRequest(int requestId, LocationRequestOptions options) {
            this.requestId = requestId;
            this.options = options;
        }

        @Override
        protected void onCancel() {
            if (!isDone()) {
                sendMessage(LocationService.MSG_CANCEL_SINGLE_LOCATION_REQUEST, requestId, null);
            }

            synchronized (singleLocationRequests) {
                singleLocationRequests.remove(requestId);
            }
        }

        /**
         * Sends the single location request
         */
        synchronized void sendLocationRequest() {
            if (isDone()) {
                return;
            }

            Bundle data = new Bundle();
            data.putParcelable(LocationService.EXTRA_LOCATION_REQUEST_OPTIONS, options);
            sendMessage(LocationService.MSG_REQUEST_SINGLE_LOCATION, requestId, data);
        }
    }
}
