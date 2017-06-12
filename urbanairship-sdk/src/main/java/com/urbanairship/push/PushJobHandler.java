/* Copyright 2017 Urban Airship and Contributors */

package com.urbanairship.push;

import android.app.Notification;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.os.Looper;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.annotation.VisibleForTesting;
import android.support.v4.app.NotificationManagerCompat;

import com.urbanairship.CoreReceiver;
import com.urbanairship.Logger;
import com.urbanairship.PreferenceDataStore;
import com.urbanairship.UAirship;
import com.urbanairship.actions.Action;
import com.urbanairship.actions.ActionArguments;
import com.urbanairship.actions.ActionRunRequest;
import com.urbanairship.actions.ActionService;
import com.urbanairship.actions.ActionValue;
import com.urbanairship.analytics.PushArrivedEvent;
import com.urbanairship.job.Job;
import com.urbanairship.json.JsonException;
import com.urbanairship.json.JsonList;
import com.urbanairship.json.JsonValue;
import com.urbanairship.push.iam.InAppMessage;
import com.urbanairship.push.notifications.NotificationFactory;
import com.urbanairship.richpush.RichPushInbox;
import com.urbanairship.util.UAStringUtil;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

/**
 * Job handler for incoming push messages.
 */
class PushJobHandler {

    /**
     * Action sent when a push is received.
     */
    static final String ACTION_RECEIVE_MESSAGE = "com.urbanairship.push.ACTION_RECEIVE_MESSAGE";

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

    private final NotificationManagerCompat notificationManager;
    private final UAirship airship;
    private final PreferenceDataStore dataStore;
    private final Context context;
    private final NotificationManagerCompat notificationManagerCompat;

    /**
     * Default constructor.
     *
     * @param context The application context.
     * @param airship The airship instance.
     * @param dataStore The preference data store.
     */
    PushJobHandler(Context context, UAirship airship, PreferenceDataStore dataStore) {
        this(context, airship, dataStore, NotificationManagerCompat.from(context));
    }

    @VisibleForTesting
    PushJobHandler(Context context, UAirship airship, PreferenceDataStore dataStore,
                   NotificationManagerCompat notificationManager) {
        this.context = context;
        this.dataStore = dataStore;
        this.airship = airship;
        this.notificationManager = notificationManager;
        this.notificationManagerCompat = NotificationManagerCompat.from(context);
    }

    /**
     * Called to handle jobs from {@link PushManager#onPerformJob(UAirship, Job)}.
     *
     * @param job The airship job.
     * @return The job result.
     */
    @Job.JobResult
    protected int performJob(Job job) {
        switch (job.getAction()) {
            case ACTION_RECEIVE_MESSAGE:
                onMessageReceived(job);
                break;
        }

        return Job.JOB_FINISHED;
    }

    /**
     * Handles incoming messages.
     *
     * @param job The received job.
     */
    private void onMessageReceived(@NonNull Job job) {
        Bundle extras = job.getExtras();
        Bundle pushBundle = extras.getBundle(PushProviderBridge.EXTRA_PUSH_BUNDLE);
        String providerClass = extras.getString(PushProviderBridge.EXTRA_PROVIDER_CLASS);

        if (pushBundle == null || providerClass == null) {
            return;
        }

        PushProvider provider = airship.getPushManager().getPushProvider();

        if (provider == null || !provider.getClass().toString().equals(providerClass)) {
            Logger.error("Received message callback from unexpected provider " +  providerClass + ". Ignoring.");
            return;
        }

        if (!provider.isAvailable(context)) {
            Logger.error("Received message callback when provider is unavailable. Ignoring.");
            return;
        }

        if (!airship.getPushManager().isPushAvailable() || !airship.getPushManager().isPushEnabled()) {
            Logger.error("Received message when push is disabled. Ignoring.");
            return;
        }

        PushMessage message = provider.processMessage(context, pushBundle);
        if (message == null) {
            return;
        }

        if (!isUniqueCanonicalId(message.getCanonicalPushId())) {
            Logger.info("Received a duplicate push with canonical ID: " + message.getCanonicalPushId());
            return;
        }

        airship.getPushManager().setLastReceivedMetadata(message.getMetadata());
        airship.getAnalytics().addEvent(new PushArrivedEvent(message));

        if (message.isExpired()) {
            Logger.debug("Received expired push message, ignoring.");
            return;
        }

        if (message.isPing()) {
            Logger.verbose("PushJobHandler - Received UA Ping");
            return;
        }

        // Run any actions for the push
        Bundle metadata = new Bundle();
        metadata.putParcelable(ActionArguments.PUSH_MESSAGE_METADATA, message);

        // Try to run actions in the service first
        try {
            ActionService.runActions(UAirship.getApplicationContext(), message.getActions(), Action.SITUATION_PUSH_RECEIVED, metadata);
        } catch (IllegalStateException e) {
            for (Map.Entry<String, ActionValue> action : message.getActions().entrySet()) {

                ActionRunRequest.createRequest(action.getKey())
                                .setMetadata(metadata)
                                .setValue(action.getValue())
                                .setSituation(Action.SITUATION_PUSH_RECEIVED)
                                .run();
            }
        }

        // Store any pending in-app messages
        InAppMessage inAppMessage = message.getInAppMessage();
        if (inAppMessage != null) {
            Logger.debug("PushJobHandler - Received a Push with an in-app message.");
            airship.getInAppMessageManager().setPendingMessage(inAppMessage);
        }

        if (!UAStringUtil.isEmpty(message.getRichPushMessageId())) {
            Logger.debug("PushJobHandler - Received a Rich Push.");
            refreshRichPushMessages();
        }

        Integer notificationId = null;
        if (!(airship.getPushManager().getUserNotificationsEnabled() && notificationManagerCompat.areNotificationsEnabled())) {
            Logger.info("User notifications disabled. Unable to display notification for message: " + message);
        } else {
            notificationId = showNotification(message, airship.getPushManager().getNotificationFactory());
        }

        sendPushReceivedBroadcast(message, notificationId);
    }

    /**
     * Builds and displays the notification.
     *
     * @param message The push message.
     * @param factory The notification factory.
     */
    private Integer showNotification(@NonNull PushMessage message, @Nullable NotificationFactory factory) {
        int notificationId;
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

        if (notification == null) {
            return null;
        }

        if (Build.VERSION.SDK_INT < 26) {
            if (!airship.getPushManager().isVibrateEnabled() || airship.getPushManager().isInQuietTime()) {
                // Remove both the vibrate and the DEFAULT_VIBRATE flag
                notification.vibrate = null;
                notification.defaults &= ~Notification.DEFAULT_VIBRATE;
            }

            if (!airship.getPushManager().isSoundEnabled() || airship.getPushManager().isInQuietTime()) {
                // Remove both the sound and the DEFAULT_SOUND flag
                notification.sound = null;
                notification.defaults &= ~Notification.DEFAULT_SOUND;
            }
        }

        Intent contentIntent = new Intent(context, CoreReceiver.class)
                .setAction(PushManager.ACTION_NOTIFICATION_OPENED_PROXY)
                .addCategory(UUID.randomUUID().toString())
                .putExtra(PushManager.EXTRA_PUSH_MESSAGE_BUNDLE, message.getPushBundle())
                .putExtra(PushManager.EXTRA_NOTIFICATION_ID, notificationId);

        // If the notification already has an intent, add it to the extras to be sent later
        if (notification.contentIntent != null) {
            contentIntent.putExtra(PushManager.EXTRA_NOTIFICATION_CONTENT_INTENT, notification.contentIntent);
        }

        Intent deleteIntent = new Intent(context, CoreReceiver.class)
                .setAction(PushManager.ACTION_NOTIFICATION_DISMISSED_PROXY)
                .addCategory(UUID.randomUUID().toString())
                .putExtra(PushManager.EXTRA_PUSH_MESSAGE_BUNDLE, message.getPushBundle())
                .putExtra(PushManager.EXTRA_NOTIFICATION_ID, notificationId);

        if (notification.deleteIntent != null) {
            deleteIntent.putExtra(PushManager.EXTRA_NOTIFICATION_DELETE_INTENT, notification.deleteIntent);
        }

        notification.contentIntent = PendingIntent.getBroadcast(context, 0, contentIntent, 0);
        notification.deleteIntent = PendingIntent.getBroadcast(context, 0, deleteIntent, 0);

        Logger.info("Posting notification: " + notification + " id: " + notificationId + " tag: " + message.getNotificationTag());
        notificationManager.notify(message.getNotificationTag(), notificationId, notification);
        return notificationId;
    }

    /**
     * Broadcasts an intent to notify the host application of a push message received, but
     * only if a receiver is set to get the user-defined intent receiver.
     *
     * @param message The message that created the notification
     * @param notificationId The ID of the messages created notification
     */
    private void sendPushReceivedBroadcast(@NonNull PushMessage message, @Nullable Integer notificationId) {
        Intent intent = new Intent(PushManager.ACTION_PUSH_RECEIVED)
                .putExtra(PushManager.EXTRA_PUSH_MESSAGE_BUNDLE, message.getPushBundle())
                .addCategory(UAirship.getPackageName())
                .setPackage(UAirship.getPackageName());

        if (notificationId != null) {
            intent.putExtra(PushManager.EXTRA_NOTIFICATION_ID, notificationId.intValue());
        }

        context.sendBroadcast(intent, UAirship.getUrbanAirshipPermission());
    }

    /**
     * Helper method that blocks while the rich push messages are refreshing
     */
    private void refreshRichPushMessages() {
        final Semaphore semaphore = new Semaphore(0);
        airship.getInbox().fetchMessages(new RichPushInbox.FetchMessagesCallback() {
            @Override
            public void onFinished(boolean success) {
                semaphore.release();
            }
        }, Looper.getMainLooper());

        try {
            semaphore.tryAcquire(RICH_PUSH_REFRESH_WAIT_TIME_MS, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            Logger.warn("Interrupted while waiting for rich push messages to refresh");
        }
    }

    /**
     * Check to see if we've seen this ID before. If we have,
     * return false. If not, add the ID to our history and return true.
     *
     * @param canonicalId The canonical push ID for an incoming notification.
     * @return <code>false</code> if the ID exists in the history, otherwise <code>true</code>.
     */
    private boolean isUniqueCanonicalId(@Nullable String canonicalId) {
        if (UAStringUtil.isEmpty(canonicalId)) {
            return true;
        }

        JsonList jsonList = null;
        try {
            jsonList = JsonValue.parseString(dataStore.getString(LAST_CANONICAL_IDS_KEY, null)).getList();
        } catch (JsonException e) {
            Logger.debug("PushJobHandler - Unable to parse canonical Ids.", e);
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
        dataStore.put(LAST_CANONICAL_IDS_KEY, JsonValue.wrapOpt(canonicalIds).toString());

        return true;
    }

}
