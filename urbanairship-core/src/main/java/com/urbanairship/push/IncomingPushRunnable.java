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
import android.support.v4.app.NotificationManagerCompat;
import android.util.Log;

import com.urbanairship.ActivityMonitor;
import com.urbanairship.CoreReceiver;
import com.urbanairship.Logger;
import com.urbanairship.UAirship;
import com.urbanairship.actions.Action;
import com.urbanairship.actions.ActionArguments;
import com.urbanairship.actions.ActionRunRequest;
import com.urbanairship.actions.ActionService;
import com.urbanairship.actions.ActionValue;
import com.urbanairship.analytics.PushArrivedEvent;
import com.urbanairship.job.JobDispatcher;
import com.urbanairship.job.JobInfo;
import com.urbanairship.json.JsonMap;
import com.urbanairship.push.notifications.NotificationFactory;
import com.urbanairship.util.Checks;
import com.urbanairship.util.ManifestUtils;
import com.urbanairship.util.UAStringUtil;

import java.util.Map;
import java.util.UUID;

import static com.urbanairship.push.PushProviderBridge.EXTRA_PROVIDER_CLASS;
import static com.urbanairship.push.PushProviderBridge.EXTRA_PUSH;


/**
 * Runnable that processes an incoming push.
 */
class IncomingPushRunnable implements Runnable {

    private static final long AIRSHIP_WAIT_TIME_MS = 5000; // 5 seconds.

    private static final long LONG_AIRSHIP_WAIT_TIME_MS = 10000; // 10 seconds.


    private final Context context;
    private final PushMessage message;
    private final String providerClass;
    private final NotificationManagerCompat notificationManager;
    private boolean isLongRunning;
    private boolean isProcessed;
    private final Runnable onFinish;
    private final JobDispatcher jobDispatcher;

    /**
     * Default constructor.
     *
     * @param builder A builder instance.
     */
    private IncomingPushRunnable(@NonNull Builder builder) {
        this.context = builder.context;
        this.message = builder.message;
        this.providerClass = builder.providerClass;
        this.isLongRunning = builder.isLongRunning;
        this.isProcessed = builder.isProcessed;
        this.onFinish = builder.onFinish;
        this.notificationManager = builder.notificationManager == null ? NotificationManagerCompat.from(context) : builder.notificationManager;
        this.jobDispatcher = builder.jobDispatcher == null ? JobDispatcher.shared(context) : builder.jobDispatcher;
    }

    @Override
    public void run() {
        long airshipWaitTime = isLongRunning ? LONG_AIRSHIP_WAIT_TIME_MS : AIRSHIP_WAIT_TIME_MS;
        UAirship airship = UAirship.waitForTakeOff(airshipWaitTime);

        if (airship == null) {
            Logger.error("Unable to process push, Airship is not ready. Make sure takeOff is called by either using autopilot or by calling takeOff in the application's onCreate method.");
            if (onFinish != null) {
                onFinish.run();
            }

            return;
        }

        if (checkProvider(airship, providerClass)) {
            // If we've already processed the push, proceed to notification display
            if (isProcessed) {
                postProcessPush(airship);
            } else {
                processPush(airship);
            }
        }

        if (onFinish != null) {
            onFinish.run();
        }
    }

    /**
     * Starts processing the push.
     *
     * @param airship The airship instance.
     */
    private void processPush(UAirship airship) {
        Logger.info("Processing push: " + message);

        if (!airship.getPushManager().isPushEnabled()) {
            Logger.info("Push disabled, ignoring message");
            return;
        }

        if (!airship.getPushManager().isComponentEnabled()) {
            Logger.info("PushManager component is disabled, ignoring message.");
            return;
        }

        if (!airship.getPushManager().isUniqueCanonicalId(message.getCanonicalPushId())) {
            Logger.info("Received a duplicate push with canonical ID: " + message.getCanonicalPushId());
            return;
        }
        if (message.isExpired()) {
            Logger.debug("Received expired push message, ignoring.");
            return;
        }

        if (message.isPing()) {
            Logger.verbose("PushJobHandler - Received UA Ping");
            return;
        }

        // Refresh remote data
        if (message.isRemoteData()) {
            airship.getRemoteData().refresh();
        }

        // Refresh inbox
        if (!UAStringUtil.isEmpty(message.getRichPushMessageId()) && airship.getInbox().getMessage(message.getRichPushMessageId()) == null) {
            Logger.debug("PushJobHandler - Received a Rich Push.");
            airship.getInbox().fetchMessages();
        }

        // Run the push actions
        runActions();

        // Notify components of the push
        airship.getLegacyInAppMessageManager().onPushReceived(message);
        airship.getAnalytics().addEvent(new PushArrivedEvent(message));
        airship.getPushManager().setLastReceivedMetadata(message.getMetadata());

        // Finish processing the push
        postProcessPush(airship);
    }

    /**
     * Finishes processing the push. This step builds the notification if applicable and
     * notifies the airship receiver if the notification was posted or cancelled.
     *
     * @param airship The airship instance.
     */
    private void postProcessPush(UAirship airship) {
        if (!airship.getPushManager().isOptIn()) {
            Logger.info("User notifications opted out. Unable to display notification for message: " + message);
            sendPushResultBroadcast(null);
            return;
        }

        NotificationFactory factory = airship.getPushManager().getNotificationFactory();

        if (factory == null) {
            Logger.info("NotificationFactory is null. Unable to display notification for message: " + message);
            sendPushResultBroadcast(null);
            return;
        }

        if (!isLongRunning && factory.requiresLongRunningTask(message)) {
            Logger.info("Push requires a long running task. Scheduled for a later time: " + message);
            reschedulePush(message);
            return;
        }

        int notificationId = 0;
        NotificationFactory.Result result;
        try {
            notificationId = factory.getNextId(message);
            result = factory.createNotificationResult(message, notificationId);
        } catch (Exception e) {
            Logger.error("Cancelling notification display to create and display notification.", e);
            result = NotificationFactory.Result.cancel();
        }

        Logger.debug("IncomingPushRunnable - Received result status " + result.getStatus() + " for push message: " + message);

        switch (result.getStatus()) {
            case NotificationFactory.Result.OK:
                postNotification(airship, result.getNotification(), notificationId);
                sendPushResultBroadcast(notificationId);
                break;
            case NotificationFactory.Result.CANCEL:
                sendPushResultBroadcast(null);
                break;
            case NotificationFactory.Result.RETRY:
                Logger.info("Scheduling notification to be retried for a later time: " + message);
                reschedulePush(message);
                break;
        }
    }

    /**
     * Posts the notification
     *
     * @param airship The airship instance.
     * @param notification The notification.
     * @param notificationId The notification ID.
     */
    private void postNotification(UAirship airship, Notification notification, int notificationId) {

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
                .addFlags(Intent.FLAG_RECEIVER_FOREGROUND)
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
    }


    /**
     * Sends the push broadcast with the notification result.
     *
     * @param notificationId The notification ID if a notification was posted.
     */
    private void sendPushResultBroadcast(@Nullable Integer notificationId) {
        Intent intent = new Intent(PushManager.ACTION_PUSH_RECEIVED)
                .putExtra(PushManager.EXTRA_PUSH_MESSAGE_BUNDLE, message.getPushBundle())
                .addCategory(UAirship.getPackageName())
                .setPackage(UAirship.getPackageName());

        if (notificationId != null) {
            intent.putExtra(PushManager.EXTRA_NOTIFICATION_ID, notificationId.intValue());
        }

        context.sendBroadcast(intent);
    }

    /**
     * Runs all the push actions for message.
     */
    private void runActions() {
        Bundle metadata = new Bundle();
        metadata.putParcelable(ActionArguments.PUSH_MESSAGE_METADATA, message);

        if (Build.VERSION.SDK_INT <= 25 || ActivityMonitor.shared(context).isAppForegrounded()) {
            try {
                ActionService.runActions(context, message.getActions(), Action.SITUATION_PUSH_RECEIVED, metadata);
                return;
            } catch (IllegalStateException e) {
                Logger.verbose("Unable to push actions in a service.");
            }
        }

        for (Map.Entry<String, ActionValue> action : message.getActions().entrySet()) {

            ActionRunRequest.createRequest(action.getKey())
                            .setMetadata(metadata)
                            .setValue(action.getValue())
                            .setSituation(Action.SITUATION_PUSH_RECEIVED)
                            .run();
        }
    }

    /**
     * Checks if the message should be processed for the given provider.
     *
     * @param airship The airship instance.
     * @param providerClass The provider class.
     * @return {@code true} if the message should be processed, otherwise {@code false}.
     */
    private boolean checkProvider(UAirship airship, String providerClass) {
        PushProvider provider = airship.getPushManager().getPushProvider();

        if (provider == null || !provider.getClass().toString().equals(providerClass)) {
            Logger.error("Received message callback from unexpected provider " + providerClass + ". Ignoring.");
            return false;
        }

        if (!provider.isAvailable(context)) {
            Logger.error("Received message callback when provider is unavailable. Ignoring.");
            return false;
        }

        if (!airship.getPushManager().isPushAvailable() || !airship.getPushManager().isPushEnabled()) {
            Logger.error("Received message when push is disabled. Ignoring.");
            return false;
        }

        if (!airship.getPushManager().getPushProvider().isUrbanAirshipMessage(context, airship, message)) {
            Logger.debug("Ignoring push: " + message);
            return false;
        }

        return true;
    }

    /**
     * Reschedules the push to finish processing at a later time.
     *
     * @param message The push message.
     */
    private void reschedulePush(@NonNull PushMessage message) {
        if (!ManifestUtils.isPermissionGranted(Manifest.permission.RECEIVE_BOOT_COMPLETED)) {
            Logger.error("Notification factory requested long running task but the application does not define RECEIVE_BOOT_COMPLETED in the manifest. Notification will be lost if the device reboots before the notification is processed.");
        }

        JobInfo jobInfo = JobInfo.newBuilder()
                                 .setAction(PushManagerJobHandler.ACTION_DISPLAY_NOTIFICATION)
                                 .generateUniqueId(context)
                                 .setAirshipComponent(PushManager.class)
                                 .setPersistent(true)
                                 .setExtras(JsonMap.newBuilder()
                                                   .putOpt(EXTRA_PUSH, message)
                                                   .put(EXTRA_PROVIDER_CLASS, providerClass)
                                                   .build())
                                 .build();

        jobDispatcher.dispatch(jobInfo);
    }

    /**
     * IncomingPushRunnable builder.
     */
    static class Builder {
        private final Context context;
        private PushMessage message;
        private String providerClass;
        private boolean isLongRunning;
        private boolean isProcessed;
        private Runnable onFinish;
        private NotificationManagerCompat notificationManager;
        private JobDispatcher jobDispatcher;

        /**
         * Default constructor.
         *
         * @param context The application context.
         */
        Builder(@NonNull Context context) {
            this.context = context.getApplicationContext();
        }

        /**
         * Sets the push message.
         *
         * @param message The push message.
         * @return The builder instance.
         */
        Builder setMessage(@NonNull PushMessage message) {
            this.message = message;
            return this;
        }

        /**
         * Sets the provider class.
         *
         * @param providerClass The provider class.
         * @return The builder instance.
         */
        Builder setProviderClass(@NonNull String providerClass) {
            this.providerClass = providerClass;
            return this;
        }

        /**
         * Sets if the runnable is long running or not.
         *
         * @param longRunning If the runnable is long running or not.
         * @return The builder instance.
         */
        Builder setLongRunning(boolean longRunning) {
            isLongRunning = longRunning;
            return this;
        }

        /**
         * Sets if the push has been processed. If so, the runnable
         * will proceed directly to notification display.
         *
         * @param processed <code>true </code>If the push has been processed, otherwise
         * <code>false</code>.
         * @return The builder instance.
         */
        Builder setProcessed(boolean processed) {
            isProcessed = processed;
            return this;
        }

        /**
         * Sets a callback when the runnable is finished.
         *
         * @param runnable A runnable.
         * @return The builder instance.
         */
        Builder setOnFinish(@NonNull Runnable runnable) {
            this.onFinish = runnable;
            return this;
        }

        /**
         * Sets the notification manager.
         *
         * @param notificationManager The notification manager.
         * @return The builder instance.
         */
        Builder setNotificationManager(@NonNull NotificationManagerCompat notificationManager) {
            this.notificationManager = notificationManager;
            return this;
        }

        /**
         * Sets the job dispatcher.
         *
         * @param jobDispatcher The job dispatcher.
         * @return The builder instance.
         */
        Builder setJobDispatcher(@NonNull JobDispatcher jobDispatcher) {
            this.jobDispatcher = jobDispatcher;
            return this;
        }

        /**
         * Builds the runnable.
         *
         * @return A {@link IncomingPushRunnable}.
         * @throws IllegalArgumentException if provider and/or push message is missing.
         */
        IncomingPushRunnable build() {
            Checks.checkNotNull(providerClass, "Provider class missing");
            Checks.checkNotNull(message, "Push Message missing");

            return new IncomingPushRunnable(this);
        }
    }


}
