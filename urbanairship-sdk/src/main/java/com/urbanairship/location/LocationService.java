/* Copyright 2016 Urban Airship and Contributors */

package com.urbanairship.location;

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
     * Time to wait for UAirship when processing messages.
     */
    private static final long AIRSHIP_WAIT_TIME_MS = 10000; // 10 seconds

    /**
     * Extra for location request options.
     */
    static final String EXTRA_LOCATION_REQUEST_OPTIONS = "com.urbanairship.location.EXTRA_LOCATION_REQUEST_OPTIONS";

    /**
     * Action to check if location updates need to be started or stopped.
     */
    static final String ACTION_CHECK_LOCATION_UPDATES = "com.urbanairship.location.ACTION_CHECK_LOCATION_UPDATES";


    private final Set<Messenger> subscribedClients = new HashSet<>();
    private final HashMap<Messenger, SparseArray<PendingResult<Location>>> pendingResultMap = new HashMap<>();

    private Messenger messenger;

    IncomingHandler handler;
    UALocationProvider locationProvider;
    Looper looper;

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
                onCheckLocationUpdates();
                break;
            case UALocationProvider.ACTION_LOCATION_UPDATE:
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
        PendingResult<Location> pendingResult = locationProvider.requestSingleLocation(new LocationCallback() {
            @Override
            public void onResult(Location location) {

                Logger.verbose("LocationService - Single location received for client: " + client + " ID: " + requestId);
                Logger.info("Received single location update: " + location);

                UAirship.shared().getAnalytics().recordLocation(location, options, LocationEvent.UPDATE_TYPE_SINGLE);

                // Send the client the location
                sendClientMessage(client, MSG_SINGLE_REQUEST_RESULT, requestId, location);
                // Remove the request
                removePendingResult(client, requestId);
            }
        }, options);

        if (pendingResult == null) {
            Logger.warn("Location service unable to perform single location request. " +
                    "UALocationProvider failed to request a location.");
            sendClientMessage(client, MSG_SINGLE_REQUEST_RESULT, requestId, null);
            return;
        }

        addPendingResult(client, requestId, pendingResult);
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
        if (!UAirship.shared().getLocationManager().isContinuousLocationUpdatesAllowed()) {
            // Location is disabled and will be stopped in another intent.
            return;
        }

        // If a provider is enabled or disabled notify the adapters so they can update providers.
        if (intent.hasExtra(LocationManager.KEY_PROVIDER_ENABLED)) {
            Logger.debug("LocationService - One of the location providers was enabled or disabled.");

            LocationRequestOptions options = UAirship.shared().getLocationManager().getLocationRequestOptions();
            locationProvider.connect();
            locationProvider.onSystemLocationProvidersChanged(options);
            return;
        }


        // Fused location sometimes has an "Unmarshalling unknown type" runtime exception on 4.4.2 devices
        Location location;
        try {
            location = (Location) (intent.hasExtra(LocationManager.KEY_LOCATION_CHANGED) ?
                        intent.getParcelableExtra(LocationManager.KEY_LOCATION_CHANGED) :
                        intent.getParcelableExtra("com.google.android.location.LOCATION"));
        } catch (Exception e) {
            Logger.error("Unable to process extract location.", e);
            return;
        }

        if (location != null) {
            Logger.info("Received location update: " + location);
            lastLocationUpdate = location;
            LocationRequestOptions options = UAirship.shared().getLocationManager().getLocationRequestOptions();

            UAirship.shared()
                    .getAnalytics()
                    .recordLocation(location, options, LocationEvent.UPDATE_TYPE_CONTINUOUS);

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
     */
    private void onCheckLocationUpdates() {
        if (UAirship.shared().getLocationManager().isContinuousLocationUpdatesAllowed()) {
            LocationRequestOptions options = UAirship.shared().getLocationManager().getLocationRequestOptions();
            LocationRequestOptions lastLocationOptions = UAirship.shared().getLocationManager().getLastUpdateOptions();

            if (!options.equals(lastLocationOptions) || !locationProvider.isUpdatesRequested()) {
                locationProvider.connect();
                locationProvider.requestLocationUpdates(options);
                UAirship.shared().getLocationManager().setLastUpdateOptions(options);
            }

            return;
        }

        Logger.debug("LocationService - Stopping updates.");
        locationProvider.connect();
        locationProvider.cancelRequests();
    }

    /**
     * Adds a single location request listener for a given client and request id.
     *
     * @param client The client who made the single location request.
     * @param requestId The request id of the location update.
     * @param pendingResult The pending location result.
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
     * @return The pending location result if removed, or null.
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

            final UAirship airship = UAirship.waitForTakeOff(AIRSHIP_WAIT_TIME_MS);
            if (airship == null) {
                Logger.error("LocationService - UAirship not ready. Dropping msg:" + msg);
                if (msg.what == MSG_HANDLE_INTENT) {
                    stopSelf(msg.arg1);
                }
                return;
            }

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
