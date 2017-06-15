/* Copyright 2017 Urban Airship and Contributors */

package com.urbanairship.push;

import android.Manifest;
import android.app.Notification;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
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
import com.urbanairship.util.ManifestUtils;
import com.urbanairship.util.UAStringUtil;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

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
        switch (job.getJobInfo().getAction()) {
            case ACTION_RECEIVE_MESSAGE:
                return onMessageReceived(job);
        }

        return Job.JOB_FINISHED;
    }

    /**
     * Handles incoming messages.
     *
     * @param job The received job.
     */
    private @Job.JobResult int onMessageReceived(@NonNull Job job) {
        Bundle extras = job.getJobInfo().getExtras();
        Bundle pushBundle = extras.getBundle(PushProviderBridge.EXTRA_PUSH_BUNDLE);
        String providerClass = extras.getString(PushProviderBridge.EXTRA_PROVIDER_CLASS);

        if (pushBundle == null || providerClass == null) {
            return Job.JOB_FINISHED;
        }

        PushProvider provider = airship.getPushManager().getPushProvider();

        if (provider == null || !provider.getClass().toString().equals(providerClass)) {
            Logger.error("Received message callback from unexpected provider " +  providerClass + ". Ignoring.");
            return Job.JOB_FINISHED;
        }

        if (!provider.isAvailable(context)) {
            Logger.error("Received message callback when provider is unavailable. Ignoring.");
            return Job.JOB_FINISHED;
        }

        if (!airship.getPushManager().isPushAvailable() || !airship.getPushManager().isPushEnabled()) {
            Logger.error("Received message when push is disabled. Ignoring.");
            return Job.JOB_FINISHED;
        }

        PushMessage message = provider.processMessage(context, pushBundle);
        if (message == null) {
            return Job.JOB_FINISHED;
        }

        if (!UAStringUtil.isEmpty(message.getRichPushMessageId()) && airship.getInbox().getMessage(message.getRichPushMessageId()) == null) {
            Logger.debug("PushJobHandler - Received a Rich Push.");
            airship.getInbox().fetchMessages();
        }

        NotificationFactory factory = airship.getPushManager().getNotificationFactory();

        if (factory != null && !job.isLongRunning() && factory.requiresLongRunningTask(message)) {
            Logger.info("Push requires a long running task. Scheduled for a later time: " + message);

            if (!ManifestUtils.isPermissionGranted(Manifest.permission.RECEIVE_BOOT_COMPLETED)) {
                Logger.error("Notification factory requested long running task but the application does not define RECEIVE_BOOT_COMPLETED in the manifest. Notification will be lost if the device reboots before the notification is processed.");
            }

            return Job.JOB_RETRY;
        }

        if (!isUniqueCanonicalId(message.getCanonicalPushId())) {
            Logger.info("Received a duplicate push with canonical ID: " + message.getCanonicalPushId());
            return Job.JOB_FINISHED;
        }

        airship.getPushManager().setLastReceivedMetadata(message.getMetadata());
        airship.getAnalytics().addEvent(new PushArrivedEvent(message));

        if (message.isExpired()) {
            Logger.debug("Received expired push message, ignoring.");
            return Job.JOB_FINISHED;
        }

        if (message.isPing()) {
            Logger.verbose("PushJobHandler - Received UA Ping");
            return Job.JOB_FINISHED;
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


        Integer notificationId = null;
        if (!(airship.getPushManager().getUserNotificationsEnabled() && notificationManagerCompat.areNotificationsEnabled())) {
            Logger.info("User notifications disabled. Unable to display notification for message: " + message);
        } else {
            notificationId = showNotification(message, airship.getPushManager().getNotificationFactory());
        }

        sendPushReceivedBroadcast(message, notificationId);

        return Job.JOB_FINISHED;
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
