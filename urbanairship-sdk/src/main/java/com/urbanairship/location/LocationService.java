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

import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.location.Location;
import android.location.LocationManager;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.Looper;
import android.os.Message;
import android.os.Messenger;
import android.os.RemoteException;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.util.SparseArray;

import com.urbanairship.Autopilot;
import com.urbanairship.Logger;
import com.urbanairship.PendingResult;
import com.urbanairship.UAirship;
import com.urbanairship.analytics.LocationEvent;
import com.urbanairship.json.JsonException;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * A service that handles requesting location from either the Fused Location
 * Provider or standard Android location.
 */
public class LocationService extends Service {

    /**
     * Preference data store key to store that last requested options for location updates.
     */
    private static final String LAST_REQUESTED_LOCATION_OPTIONS_KEY = "com.urbanairship.location.LAST_REQUESTED_LOCATION_OPTIONS";

    /**
     * The max age in milliseconds of the last location update to send to new subscribers.
     */
    private static final long NEW_SUBSCRIBER_LAST_LOCATION_MS = 5000;

    /**
     * Command to the service to subscribe to location updates. The Message's
     * replyTo field must be a Messenger of the client where updates should
     * be sent.
     */
    static final int MSG_SUBSCRIBE_UPDATES = 1;

    /**
     * Command to the service to unsubscribe to location updates. The Message's
     * replyTo field must be a Messenger of the client as previously given
     * with MSG_SUBSCRIBE_UPDATES.
     */
    static final int MSG_UNSUBSCRIBE_UPDATES = 2;

    /**
     * Command that the service sends out to any clients subscribed to updates
     * when a new location is received. The location will be supplied as the
     * Message's obj field.
     */
    static final int MSG_NEW_LOCATION_UPDATE = 3;

    /**
     * Command that the service sends out back to the client that requested the
     * single location request. The location will be supplied as the Message's
     * obj field and the request id will be supplied as the Message's arg1 field.
     */
    static final int MSG_SINGLE_REQUEST_RESULT = 4;

    /**
     * Command to the service to request a single location update.
     * LocationRequestOptions must be supplied as the Message's data field and
     * the request id of as the Message's arg1 field.
     */
    static final int MSG_REQUEST_SINGLE_LOCATION = 5;

    /**
     * Command to the service to cancel a single location request. The request
     * id to cancel is supplied as the Message's arg1 field.
     */
    static final int MSG_CANCEL_SINGLE_LOCATION_REQUEST = 6;

    /**
     * Command to the service to run intents received during the onStartCommand
     * method on the thread handler.
     */
    private static final int MSG_HANDLE_INTENT = 7;

    /**
     * Extra for location request options.
     */
    static final String EXTRA_LOCATION_REQUEST_OPTIONS = "com.urbanairship.location.EXTRA_LOCATION_REQUEST_OPTIONS";

    /**
     * Action to check if location updates need to be started or stopped.
     */
    static final String ACTION_CHECK_LOCATION_UPDATES = "com.urbanairship.location.ACTION_CHECK_LOCATION_UPDATES";

    /**
     * Action used for location updates.
     */
    static final String ACTION_LOCATION_UPDATE = "com.urbanairship.location.ACTION_LOCATION_UPDATE";

    private final Set<Messenger> subscribedClients = new HashSet<>();
    private final HashMap<Messenger, SparseArray<PendingResult<Location>>> pendingResultMap = new HashMap<>();

    private Messenger messenger;

    IncomingHandler handler;
    UALocationProvider locationProvider;
    Looper looper;


    /**
     * We process incoming intents on a different thread, so its possible to
     * to have a stop updates action and an update action on the queue at the
     * same time. We need a flag to indicate stop so we can drop location updates.
     */
    static boolean areUpdatesStopped = false;

    /**
     * Stores the last updated LocationRequestOptions. We track this value for location updates so when the
     * application is started from a location update we do not request updates again using the same
     * update options. This avoid double location fixes.
     */
    static LocationRequestOptions lastUpdateOptions = null;

    private Location lastLocationUpdate;


    @Override
    public IBinder onBind(Intent intent) {
        return messenger.getBinder();
    }

    @Override
    public void onDestroy() {
        locationProvider.disconnect();
        looper.quit();
        super.onDestroy();
        Logger.verbose("LocationService - Service destroyed.");
    }

    @Override
    public void onCreate() {
        super.onCreate();
        Autopilot.automaticTakeOff(getApplicationContext());

        HandlerThread handlerThread = new HandlerThread("LocationService");
        handlerThread.start();

        looper = handlerThread.getLooper();
        handler = new IncomingHandler(looper);
        messenger = new Messenger(handler);

        locationProvider = new UALocationProvider(getApplicationContext());

        Logger.verbose("LocationService - Service created.");
    }

    @Override
    public int onStartCommand(final Intent intent, int flags, final int startId) {
        Message msg = handler.obtainMessage();
        msg.arg1 = startId;
        msg.obj = intent;
        msg.what = MSG_HANDLE_INTENT;

        handler.sendMessage(msg);

        return START_NOT_STICKY;
    }


    private void onHandleIntent(Intent intent) {
        if (intent == null || intent.getAction() == null) {
            return;
        }

        Logger.verbose("LocationService - Received intent with action: " + intent.getAction());

        switch (intent.getAction()) {
            case ACTION_CHECK_LOCATION_UPDATES:
                onCheckLocationUpdates(intent);
                break;
            case ACTION_LOCATION_UPDATE:
                onLocationUpdate(intent);
                break;
        }
    }

    /**
     * Called when a message was received to subscribe for updates.
     *
     * @param message The received message.
     */
    private void onSubscribeUpdates(@NonNull Message message) {
        if (message.replyTo != null) {
            Logger.debug("LocationService - Client subscribed for updates: " + message.replyTo);
            subscribedClients.add(message.replyTo);

            if (lastLocationUpdate != null && (System.currentTimeMillis() - lastLocationUpdate.getTime()) < NEW_SUBSCRIBER_LAST_LOCATION_MS) {
                if (!sendClientMessage(message.replyTo, MSG_NEW_LOCATION_UPDATE, 0, lastLocationUpdate)) {
                    // Client died or is unable to receive messages, remove it
                    subscribedClients.remove(message.replyTo);
                }
            }
        }
    }

    /**
     * Called when a message was received to unsubscribe for updates.
     *
     * @param message The received message.
     */
    private void onUnsubscribeUpdates(@NonNull Message message) {
        if (subscribedClients.remove(message.replyTo)) {
            Logger.debug("LocationService - Client unsubscribed from updates: " + message.replyTo);
        }
    }

    /**
     * Called when a message was received to request a single location update.
     *
     * @param message The received message.
     */
    private void onRequestSingleUpdate(@NonNull final Message message) {
        final int requestId = message.arg1;
        final Messenger client = message.replyTo;

        final LocationRequestOptions options = message.getData().getParcelable(EXTRA_LOCATION_REQUEST_OPTIONS);
        if (options == null) {
            Logger.warn("Location service unable to perform single location request. Missing request options.");
            sendClientMessage(client, MSG_SINGLE_REQUEST_RESULT, requestId, null);
            return;
        }

        Logger.verbose("LocationService - Single location request for client: " + client + " ID: " + requestId);
        Logger.info("Requesting single location update with request options: " + options);


        locationProvider.connect();
        PendingResult<Location> pendingResult = locationProvider.requestSingleLocation(options);

        if (pendingResult == null) {
            Logger.warn("Location service unable to perform single location request. " +
                    "UALocationProvider failed to request a location.");
            sendClientMessage(client, MSG_SINGLE_REQUEST_RESULT, requestId, null);
            return;
        }

        addPendingResult(client, requestId, pendingResult);

        pendingResult.onResult(new PendingResult.ResultCallback<Location>() {
            @Override
            public void onResult(Location location) {

                Logger.verbose("LocationService - Single location received for client: " + client + " ID: " + requestId);
                Logger.info("Received single location update: " + location);

                UAirship.shared().getAnalytics().recordLocation(location, options, LocationEvent.UpdateType.SINGLE);

                // Send the client the location
                sendClientMessage(client, MSG_SINGLE_REQUEST_RESULT, requestId, location);
                // Remove the request
                removePendingResult(client, requestId);
            }
        });
    }

    /**
     * Called when a message was received to cancel a single location update.
     *
     * @param message The received message.
     */
    private void onCancelSingleUpdate(@NonNull Message message) {
        final int requestId = message.arg1;
        final Messenger client = message.replyTo;

        PendingResult<Location> pendingResult = removePendingResult(client, requestId);
        if (pendingResult != null) {
            Logger.debug("LocationService - Canceled single request for client: " + client + " ID: " + requestId);
            pendingResult.cancel();
        }
    }

    /**
     * Called when an intent is received with action ACTION_LOCATION_UPDATE.
     *
     * @param intent The received intent.
     */
    private void onLocationUpdate(@NonNull Intent intent) {
        if (!UAirship.shared().getLocationManager().isContinuousLocationUpdatesAllowed() || areUpdatesStopped) {
            // Location is disabled and will be stopped in another intent.
            return;
        }

        /*
         * Set the last location options from the location update if
         * its not already set. This should only happen when the application
         * is started from a location update. Used to prevent an unnecessary
         * request for  a new location fix right after the application was
         * started from a location update.
         */
        if (lastUpdateOptions == null) {
            String jsonString = UAirship.shared()
                                        .getLocationManager()
                                        .getPreferenceDataStore()
                                        .getString(LAST_REQUESTED_LOCATION_OPTIONS_KEY, null);

            if (jsonString != null) {
                try {
                    lastUpdateOptions = LocationRequestOptions.parseJson(jsonString);
                } catch (JsonException e) {
                    Logger.error("LocationService - Failed parsing LocationRequestOptions from JSON: " + e.getMessage());
                } catch (IllegalArgumentException e) {
                    Logger.error("LocationService - Invalid LocationRequestOptions from JSON: " + e.getMessage());
                }
            }
        }

        /*
         * If a provider is enabled or disabled we can assume we were
         * using standard location. Stop any location updates and start
         * it again so it picks up the best location provider.
         */
        if (intent.hasExtra(LocationManager.KEY_PROVIDER_ENABLED)) {
            Logger.debug("LocationService - Restarting location updates. " +
                    "One of the location providers was enabled or disabled.");

            LocationRequestOptions options = UAirship.shared().getLocationManager().getLocationRequestOptions();
            PendingIntent pendingIntent = createLocationUpdateIntent();

            // Store the last requested options so we can restore them later
            // if the application starts from a location update being received.
            UAirship.shared()
                    .getLocationManager()
                    .getPreferenceDataStore()
                    .put(LAST_REQUESTED_LOCATION_OPTIONS_KEY, options);

            locationProvider.connect();
            locationProvider.cancelRequests(pendingIntent);
            locationProvider.requestLocationUpdates(options, pendingIntent);

            return;
        }


        Location location = (Location) (intent.hasExtra(LocationManager.KEY_LOCATION_CHANGED) ?
                                        intent.getParcelableExtra(LocationManager.KEY_LOCATION_CHANGED) :
                                        intent.getParcelableExtra("com.google.android.location.LOCATION"));

        if (location != null) {

            lastLocationUpdate = location;

            Logger.info("Received location update: " + location);

            LocationRequestOptions options = lastUpdateOptions == null ? UAirship.shared().getLocationManager().getLocationRequestOptions() : lastUpdateOptions;

            UAirship.shared()
                    .getAnalytics()
                    .recordLocation(location, options, LocationEvent.UpdateType.CONTINUOUS);

            List<Messenger> clientCopy = new ArrayList<>(subscribedClients);
            for (Messenger client : clientCopy) {
                if (!sendClientMessage(client, MSG_NEW_LOCATION_UPDATE, 0, location)) {
                    // Client died or is unable to receive messages, remove it
                    subscribedClients.remove(client);
                }
            }
        }
    }

    /**
     * Called when an intent is received with action {@link #ACTION_CHECK_LOCATION_UPDATES}. Starts
     * or stops location updates.
     *
     * @param intent The received intent.
     */
    private void onCheckLocationUpdates(@NonNull Intent intent) {
        if (UAirship.shared().getLocationManager().isContinuousLocationUpdatesAllowed()) {
            LocationRequestOptions options = UAirship.shared().getLocationManager().getLocationRequestOptions();

            /*
             * Canceling and starting location updates causes the provider to request
             * another fix, so skip requesting it again if we already are requesting.
             */
            if (lastUpdateOptions == null || !lastUpdateOptions.equals(options)) {
                Logger.debug("LocationService - Starting updates.");

                // Store the last requested options so we can restore them later
                // if the application starts from a location update being received.
                UAirship.shared()
                        .getLocationManager()
                        .getPreferenceDataStore()
                        .put(LAST_REQUESTED_LOCATION_OPTIONS_KEY, options);

                lastUpdateOptions = options;
                areUpdatesStopped = false;

                PendingIntent pendingIntent = createLocationUpdateIntent();

                locationProvider.connect();
                locationProvider.cancelRequests(pendingIntent);

                locationProvider.requestLocationUpdates(options, pendingIntent);

            }

        } else if (!areUpdatesStopped) {
            Logger.debug("LocationService - Stopping updates.");
            locationProvider.cancelRequests(createLocationUpdateIntent());
            lastUpdateOptions = null;
            areUpdatesStopped = true;
        }
    }

    /**
     * Adds a single location request listener for a given client and request id.
     *
     * @param client The client who made the single location request.
     * @param requestId The request id of the location update.
     * @param pendingResult The pending result.
     */
    private void addPendingResult(@Nullable Messenger client, int requestId, @NonNull PendingResult<Location> pendingResult) {
        synchronized (pendingResultMap) {
            if (client != null && requestId > 0) {
                if (!pendingResultMap.containsKey(client)) {
                    pendingResultMap.put(client, new SparseArray<PendingResult<Location>>());
                }
                pendingResultMap.get(client).put(requestId, pendingResult);
            }
        }
    }

    /**
     * Removes a single location request listener for a given client and request id.
     *
     * @param client The client who made the single location request.
     * @param requestId The request id of the location update.
     * @return The pending result if removed, or null.
     */
    private synchronized PendingResult<Location> removePendingResult(@Nullable Messenger client, int requestId) {
        synchronized (pendingResultMap) {
            if (!pendingResultMap.containsKey(client)) {
                return null;
            }

            SparseArray<PendingResult<Location>> providerSparseArray = pendingResultMap.get(client);
            if (providerSparseArray != null) {
                PendingResult<Location> pendingResult = providerSparseArray.get(requestId);

                providerSparseArray.remove(requestId);
                if (providerSparseArray.size() == 0) {
                    pendingResultMap.remove(client);
                }

                return pendingResult;
            }

        }
        return null;
    }

    /**
     * Sends the client a message.
     *
     * @param client The Messenger to send to.
     * @param what The message's what field.
     * @param arg1 The message's arg1 field.
     * @param obj The message's obj field.
     * @return <code>true</code> if the message sent or <code>false</code> if it failed
     * to send because the client has died or the client is null.
     */
    private boolean sendClientMessage(@Nullable Messenger client, int what, int arg1, @Nullable Object obj) {
        if (client == null) {
            return false;
        }

        try {
            client.send(Message.obtain(null, what, arg1, 0, obj));
            return true;
        } catch (RemoteException e) {
            return false;
        }
    }

    /**
     * Creates the pending intent for location updates.
     *
     * @return PendingIntent for location updates.
     */
    @NonNull
    private PendingIntent createLocationUpdateIntent() {
        Intent intent = new Intent(getApplicationContext(), LocationService.class)
                .setAction(ACTION_LOCATION_UPDATE);

        return PendingIntent.getService(getApplicationContext(), 0, intent, PendingIntent.FLAG_UPDATE_CURRENT);
    }



    /**
     * Service handler to handle communicating with clients through messages.
     */
    protected class IncomingHandler extends Handler {

        /**
         * Default IncomingHandler constructor.
         *
         * @param looper The looper to receive messages on.
         */
        public IncomingHandler(Looper looper) {
            super(looper);
        }

        @Override
        public void handleMessage(Message msg) {
            Logger.verbose("LocationService - Received message: " + msg);

            switch (msg.what) {
                case MSG_UNSUBSCRIBE_UPDATES:
                    onUnsubscribeUpdates(msg);
                    break;
                case MSG_SUBSCRIBE_UPDATES:
                    onSubscribeUpdates(msg);
                    break;
                case MSG_REQUEST_SINGLE_LOCATION:
                    onRequestSingleUpdate(msg);
                    break;
                case MSG_CANCEL_SINGLE_LOCATION_REQUEST:
                    onCancelSingleUpdate(msg);
                    break;
                case MSG_HANDLE_INTENT:
                    onHandleIntent((Intent) msg.obj);
                    stopSelf(msg.arg1);
                    break;
                default:
                    Logger.error("LocationService - Unexpected message sent to location service: " + msg);
            }
        }
    }
}
