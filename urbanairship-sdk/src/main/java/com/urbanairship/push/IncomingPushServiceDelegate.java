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

package com.urbanairship.push;

import android.app.Notification;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.support.v4.app.NotificationManagerCompat;

import com.urbanairship.BaseIntentService;
import com.urbanairship.CoreReceiver;
import com.urbanairship.Logger;
import com.urbanairship.PreferenceDataStore;
import com.urbanairship.UAirship;
import com.urbanairship.actions.ActionArguments;
import com.urbanairship.actions.ActionService;
import com.urbanairship.actions.Situation;
import com.urbanairship.analytics.PushArrivedEvent;
import com.urbanairship.json.JsonException;
import com.urbanairship.json.JsonList;
import com.urbanairship.json.JsonValue;
import com.urbanairship.push.iam.InAppMessage;
import com.urbanairship.push.notifications.NotificationFactory;
import com.urbanairship.richpush.RichPushManager;
import com.urbanairship.util.UAStringUtil;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

/**
 * Service delegate for the {@link PushService} to handle incoming push messages.
 */
class IncomingPushServiceDelegate extends BaseIntentService.Delegate {

    /**
     * Key to store the push canonical IDs for push deduping.
     */
    private static final String LAST_CANONICAL_IDS_KEY = "com.urbanairship.push.LAST_CANONICAL_IDS";

    /**
     * Max amount of canonical IDs to store.
     */
    private static final int MAX_CANONICAL_IDS = 10;

    /**
     * Amount of time in milliseconds to wait for {@link com.urbanairship.richpush.RichPushInbox} to refresh.
     */
    private static final int RICH_PUSH_REFRESH_WAIT_TIME_MS = 60000; // 1 minute

    private NotificationManagerCompat notificationManager;
    private PushManager pushManager;
    private UAirship airship;

    public IncomingPushServiceDelegate(Context context, PreferenceDataStore dataStore) {
        this(context, dataStore, UAirship.shared(), NotificationManagerCompat.from(context));
    }

    public IncomingPushServiceDelegate(Context context, PreferenceDataStore dataStore,
                                       UAirship airship, NotificationManagerCompat notificationManager) {
        super(context, dataStore);

        this.airship = airship;
        this.pushManager = airship.getPushManager();
        this.notificationManager = notificationManager;
    }

    @Override
    protected void onHandleIntent(Intent intent) {
        switch (intent.getAction()) {
            case PushService.ACTION_RECEIVE_ADM_MESSAGE:
                onAdmMessageReceived(intent);
                break;
            case PushService.ACTION_RECEIVE_GCM_MESSAGE:
                onGcmMessageReceived(intent);
                break;
        }
    }

    /**
     * Handles incoming GCM messages.
     *
     * @param intent The received intent.
     */
    private void onGcmMessageReceived(Intent intent) {
        if (airship.getPlatformType() != UAirship.ANDROID_PLATFORM) {
            Logger.error("Received intent from invalid transport acting as GCM.");
            return;
        }

        if (!airship.getPushManager().isPushAvailable()) {
            Logger.error("IncomingPushServiceDelegate - Received intent from GCM without registering.");
            return;
        }

        Intent gcmIntent = intent.getParcelableExtra(PushService.EXTRA_INTENT);
        if (gcmIntent == null) {
            Logger.error("IncomingPushServiceDelegate - Received GCM message missing original intent.");
            return;
        }

        String sender = gcmIntent.getStringExtra("from");
        if (sender != null && !sender.equals(airship.getAirshipConfigOptions().gcmSender)) {
            Logger.info("Ignoring GCM message from sender: " + sender);
            return;
        }

        if (GCMConstants.GCM_DELETED_MESSAGES_VALUE.equals(gcmIntent.getStringExtra(GCMConstants.EXTRA_GCM_MESSAGE_TYPE))) {
            Logger.info("GCM deleted " + gcmIntent.getStringExtra(GCMConstants.EXTRA_GCM_TOTAL_DELETED) + " pending messages.");
            return;
        }

        processMessage(new PushMessage(gcmIntent.getExtras()));
    }

    /**
     * Handles incoming ADM messages.
     *
     * @param intent The received intent.
     */
    private void onAdmMessageReceived(Intent intent) {
        if (airship.getPlatformType() != UAirship.AMAZON_PLATFORM) {
            Logger.error("Received intent from invalid transport acting as ADM.");
            return;
        }

        if (!airship.getPushManager().isPushAvailable()) {
            Logger.error("IncomingPushServiceDelegate - Received intent from ADM without registering.");
            return;
        }

        Intent admIntent = intent.getParcelableExtra(PushService.EXTRA_INTENT);
        if (admIntent == null) {
            Logger.error("IncomingPushServiceDelegate - Received ADM message missing original intent.");
            return;
        }

        processMessage(new PushMessage(admIntent.getExtras()));
    }

    /**
     * Processes the received message.
     *
     * @param message The push message.
     */
    private void processMessage(PushMessage message) {
        if (!pushManager.isPushEnabled()) {
            Logger.info("Received a push when push is disabled! Ignoring.");
            return;
        }

        if (!isUniqueCanonicalId(message.getCanonicalPushId())) {
            Logger.info("Received a duplicate push with canonical ID: " + message.getCanonicalPushId());
            return;
        }

        pushManager.setLastReceivedSendId(message.getSendId());
        createPushArrivedEvent(message.getSendId());

        if (message.isExpired()) {
            Logger.debug("Received expired push message, ignoring.");
            return;
        }

        if (message.isPing()) {
            Logger.verbose("IncomingPushServiceDelegate - Received UA Ping");
            return;
        }

        // Run any actions for the push
        Bundle metadata = new Bundle();
        metadata.putParcelable(ActionArguments.PUSH_MESSAGE_METADATA, message);
        ActionService.runActions(UAirship.getApplicationContext(), message.getActions(), Situation.PUSH_RECEIVED, metadata);

        // Store any pending in-app messages
        InAppMessage inAppMessage = message.getInAppMessage();
        if (inAppMessage != null) {
            Logger.debug("IncomingPushServiceDelegate - Received a Push with an in-app message.");
            airship.getInAppMessageManager().setPendingMessage(inAppMessage);
        }

        if (!UAStringUtil.isEmpty(message.getRichPushMessageId())) {
            Logger.debug("IncomingPushServiceDelegate - Received a Rich Push.");
            refreshRichPushMessages();
        }

        Integer notificationId = null;
        if (!pushManager.getUserNotificationsEnabled()) {
            Logger.info("User notifications disabled. Unable to display notification for message: " + message);
        } else {
            notificationId = showNotification(message, pushManager.getNotificationFactory());
        }

        sendPushReceivedBroadcast(message, notificationId);
    }

    /**
     * Builds and displays the notification.
     *
     * @param message The push message.
     * @param factory The notification factory.
     */
    private Integer showNotification(PushMessage message, NotificationFactory factory) {
        Integer notificationId;
        Notification notification;

        if (factory == null) {
            Logger.info("NotificationFactory is null. Unable to display notification for message: " + message);
            return null;
        }

        try {
            notificationId = factory.getNextId(message);
            notification = factory.createNotification(message, notificationId);
        } catch (Exception e) {
            Logger.error("Unable to create and display notification.", e);
            return null;
        }

        if (notification != null) {
            if (!pushManager.isVibrateEnabled() || pushManager.isInQuietTime()) {
                // Remove both the vibrate and the DEFAULT_VIBRATE flag
                notification.vibrate = null;
                notification.defaults &= ~Notification.DEFAULT_VIBRATE;
            }

            if (!pushManager.isSoundEnabled() || pushManager.isInQuietTime()) {
                // Remove both the sound and the DEFAULT_SOUND flag
                notification.sound = null;
                notification.defaults &= ~Notification.DEFAULT_SOUND;
            }

            Intent contentIntent = new Intent(getContext(), CoreReceiver.class)
                    .setAction(PushManager.ACTION_NOTIFICATION_OPENED_PROXY)
                    .addCategory(UUID.randomUUID().toString())
                    .putExtra(PushManager.EXTRA_PUSH_MESSAGE, message)
                    .putExtra(PushManager.EXTRA_NOTIFICATION_ID, notificationId);

            // If the notification already has an intent, add it to the extras to be sent later
            if (notification.contentIntent != null) {
                contentIntent.putExtra(PushManager.EXTRA_NOTIFICATION_CONTENT_INTENT, notification.contentIntent);
            }

            Intent deleteIntent = new Intent(getContext(), CoreReceiver.class)
                    .setAction(PushManager.ACTION_NOTIFICATION_DISMISSED_PROXY)
                    .addCategory(UUID.randomUUID().toString())
                    .putExtra(PushManager.EXTRA_PUSH_MESSAGE, message)
                    .putExtra(PushManager.EXTRA_NOTIFICATION_ID, notificationId);

            if (notification.deleteIntent != null) {
                deleteIntent.putExtra(PushManager.EXTRA_NOTIFICATION_DELETE_INTENT, notification.deleteIntent);
            }

            notification.contentIntent = PendingIntent.getBroadcast(getContext(), 0, contentIntent, 0);
            notification.deleteIntent = PendingIntent.getBroadcast(getContext(), 0, deleteIntent, 0);

            Logger.info("Posting notification " + notification + " with ID " + notificationId);
            notificationManager.notify(notificationId, notification);
        }

        return notificationId;
    }

    /**
     * Broadcasts an intent to notify the host application of a push message received, but
     * only if a receiver is set to get the user-defined intent receiver.
     *
     * @param message The message that created the notification
     * @param notificationId The ID of the messages created notification
     */
    private void sendPushReceivedBroadcast(PushMessage message, Integer notificationId) {
        Intent intent = new Intent(PushManager.ACTION_PUSH_RECEIVED)
                .putExtra(PushManager.EXTRA_PUSH_MESSAGE, message)
                .addCategory(UAirship.getPackageName())
                .setPackage(UAirship.getPackageName());

        if (notificationId != null) {
            intent.putExtra(PushManager.EXTRA_NOTIFICATION_ID, notificationId.intValue());
        }

        getContext().sendBroadcast(intent, UAirship.getUrbanAirshipPermission());
    }

    /**
     * Helper method that blocks while the rich push messages are refreshing
     */
    private void refreshRichPushMessages() {
        final Semaphore semaphore = new Semaphore(0);
        airship.getRichPushManager().refreshMessages(new RichPushManager.RefreshMessagesCallback() {
            @Override
            public void onRefreshMessages(boolean success) {
                semaphore.release();
            }
        });

        try {
            semaphore.tryAcquire(RICH_PUSH_REFRESH_WAIT_TIME_MS, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            Logger.warn("Interrupted while waiting for rich push messages to refresh");
        }
    }

    /**
     * Creates and adds a push arrived event
     *
     * @param sendId The send ID.
     */
    private void createPushArrivedEvent(String sendId) {
        if (UAStringUtil.isEmpty(sendId)) {
            sendId = UUID.randomUUID().toString();
        }

        airship.getAnalytics().addEvent(new PushArrivedEvent(sendId));
    }

    /**
     * Check to see if we've seen this ID before. If we have,
     * return false. If not, add the ID to our history and return true.
     *
     * @param canonicalId The canonical push ID for an incoming notification.
     * @return <code>false</code> if the ID exists in the history, otherwise <code>true</code>.
     */
    private boolean isUniqueCanonicalId(String canonicalId) {
        if (UAStringUtil.isEmpty(canonicalId)) {
            return true;
        }

        JsonList jsonList = null;
        try {
            jsonList = JsonValue.parseString(getDataStore().getString(LAST_CANONICAL_IDS_KEY, null)).getList();
        } catch (JsonException e) {
            Logger.debug("IncomingPushServiceDelegate - Unable to parse canonical Ids.", e);
        }

        List<JsonValue> canonicalIds = jsonList == null ? new ArrayList<JsonValue>() : jsonList.getList();

        // Wrap the canonicalId
        JsonValue id = JsonValue.wrap(canonicalId);

        // Check if the list contains the canonicalId
        if (canonicalIds.contains(id)) {
            return false;
        }

        // Add it
        canonicalIds.add(id);
        if (canonicalIds.size() > MAX_CANONICAL_IDS) {
            canonicalIds = canonicalIds.subList(canonicalIds.size() - MAX_CANONICAL_IDS, canonicalIds.size());
        }

        // Store the new list
        getDataStore().put(LAST_CANONICAL_IDS_KEY, JsonValue.wrap(canonicalIds, null).toString());

        return true;
    }

}
