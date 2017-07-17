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
import com.urbanairship.push.iam.InAppMessage;
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

    private final Context context;
    private final PushMessage message;
    private final String providerClass;
    private final NotificationManagerCompat notificationManager;
    private boolean isLongRunning;
    private final Runnable onFinish;

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
        this.onFinish = builder.onFinish;
        this.notificationManager = builder.notificationManager == null ? NotificationManagerCompat.from(context) : builder.notificationManager;
    }

    @Override
    public void run() {
        UAirship airship = UAirship.waitForTakeOff(5000);

        if (airship == null) {
            Logger.error("Unable to process push, Airship is not ready. Make sure takeOff is called by either using autopilot or by calling takeOff in the application's onCreate method.");
            if (onFinish != null) {
                onFinish.run();
            }

            return;
        }

        if (checkProvider(airship, providerClass, message)) {
            processPush(airship);
        }

        if (onFinish != null) {
            onFinish.run();
        }
    }

    /**
     * Processes the push notification.
     *
     * @param airship The airship instance.
     */
    private void processPush(UAirship airship) {
        if (!airship.getPushManager().isPushEnabled()) {
            Logger.info("Push disabled, ignoring message");
            return;
        }

        if (!UAStringUtil.isEmpty(message.getRichPushMessageId()) && airship.getInbox().getMessage(message.getRichPushMessageId()) == null) {
            Logger.debug("PushJobHandler - Received a Rich Push.");
            airship.getInbox().fetchMessages();
        }

        NotificationFactory factory = airship.getPushManager().getNotificationFactory();

        if (factory != null && !isLongRunning && factory.requiresLongRunningTask(message)) {
            Logger.info("Push requires a long running task. Scheduled for a later time: " + message);

            if (!ManifestUtils.isPermissionGranted(Manifest.permission.RECEIVE_BOOT_COMPLETED)) {
                Logger.error("Notification factory requested long running task but the application does not define RECEIVE_BOOT_COMPLETED in the manifest. Notification will be lost if the device reboots before the notification is processed.");
            }

            JobInfo jobInfo = JobInfo.newBuilder()
                                     .setAction(PushManagerJobHandler.ACTION_PROCESS_PUSH)
                                     .generateUniqueId(context)
                                     .setAirshipComponent(PushManager.class)
                                     .setPersistent(true)
                                     .setExtras(JsonMap.newBuilder()
                                               .putOpt(EXTRA_PUSH, message)
                                               .put(EXTRA_PROVIDER_CLASS, providerClass)
                                               .build())
                                     .build();

            JobDispatcher.shared(context).dispatch(jobInfo);

            return;
        }

        if (!airship.getPushManager().isUniqueCanonicalId(message.getCanonicalPushId())) {
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

        // Run the push actions
        runActions();

        // Store any pending in-app messages
        InAppMessage inAppMessage = message.getInAppMessage();
        if (inAppMessage != null) {
            Logger.debug("PushJobHandler - Received a Push with an in-app message.");
            airship.getInAppMessageManager().setPendingMessage(inAppMessage);
        }


        Integer notificationId = null;
        if (!airship.getPushManager().isOptIn()) {
            Logger.info("User notifications opted out. Unable to display notification for message: " + message);
        } else {
            notificationId = displayNotification(airship, message);
        }

        sendPushReceivedBroadcast(message, notificationId);
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
     * @param message The push message.
     * @return {@code true} if the message should be processed, otherwise {@code false}.
     */
    private boolean checkProvider(UAirship airship, String providerClass, PushMessage message) {
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
     * Displays the notification.
     *
     * @param airship The airship instance.
     * @param message The push message.
     * @return The resulting notification Id.
     */
    private Integer displayNotification(UAirship airship, @NonNull PushMessage message) {
        int notificationId;
        Notification notification;
        NotificationFactory factory = airship.getPushManager().getNotificationFactory();

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
     * IncomingPushRunnable builder.
     */
    static class Builder {
        private final Context context;
        private PushMessage message;
        private String providerClass;
        private boolean isLongRunning;
        private Runnable onFinish;
        private NotificationManagerCompat notificationManager;

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
         * Sets the notification manage.
         *
         * @param notificationManager The notification manager.
         * @return The builder instance.
         */
        Builder setNotificationManager(@NonNull NotificationManagerCompat notificationManager) {
            this.notificationManager = notificationManager;
            return this;
        }

        /**
         * Builds the runnable.
         *
         * @return A {@link IncomingPushRunnable}.
         *
         * @throws IllegalArgumentException if provider and/or push message is missing.
         */
        IncomingPushRunnable build() {
            Checks.checkNotNull(providerClass, "Provider class missing");
            Checks.checkNotNull(message, "Push Message missing");

            return new IncomingPushRunnable(this);
        }

    }


}
