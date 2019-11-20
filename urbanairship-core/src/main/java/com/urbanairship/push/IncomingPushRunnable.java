package com.urbanairship.push;

import android.Manifest;
import android.app.Notification;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;

import com.urbanairship.Autopilot;
import com.urbanairship.Logger;
import com.urbanairship.UAirship;
import com.urbanairship.actions.Action;
import com.urbanairship.actions.ActionArguments;
import com.urbanairship.actions.ActionRunRequest;
import com.urbanairship.actions.ActionValue;
import com.urbanairship.analytics.PushArrivedEvent;
import com.urbanairship.job.JobDispatcher;
import com.urbanairship.job.JobInfo;
import com.urbanairship.json.JsonMap;
import com.urbanairship.push.notifications.NotificationArguments;
import com.urbanairship.push.notifications.NotificationChannelCompat;
import com.urbanairship.push.notifications.NotificationChannelUtils;
import com.urbanairship.push.notifications.NotificationProvider;
import com.urbanairship.push.notifications.NotificationResult;
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
    private final boolean isLongRunning;
    private final boolean isProcessed;
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
        this.notificationManager = builder.notificationManager == null ? NotificationManagerCompat.from(context) : builder.notificationManager;
        this.jobDispatcher = builder.jobDispatcher == null ? JobDispatcher.shared(context) : builder.jobDispatcher;
    }

    @Override
    public void run() {
        Autopilot.automaticTakeOff(context);

        long airshipWaitTime = isLongRunning ? LONG_AIRSHIP_WAIT_TIME_MS : AIRSHIP_WAIT_TIME_MS;
        UAirship airship = UAirship.waitForTakeOff(airshipWaitTime);

        if (airship == null) {
            Logger.error("Unable to process push, Airship is not ready. Make sure takeOff is called by either using autopilot or by calling takeOff in the application's onCreate method.");
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
    }

    /**
     * Starts processing the push.
     *
     * @param airship The airship instance.
     */
    private void processPush(UAirship airship) {
        Logger.info("Processing push: %s", message);

        if (!airship.getPushManager().isPushEnabled()) {
            Logger.debug("Push disabled, ignoring message");
            return;
        }

        if (!airship.getPushManager().isComponentEnabled()) {
            Logger.debug("PushManager component is disabled, ignoring message.");
            return;
        }

        if (!airship.getPushManager().isUniqueCanonicalId(message.getCanonicalPushId())) {
            Logger.debug("Received a duplicate push with canonical ID: %s", message.getCanonicalPushId());
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
    private void postProcessPush(final UAirship airship) {
        if (!airship.getPushManager().isOptIn()) {
            Logger.info("User notifications opted out. Unable to display notification for message: %s", message);
            notifyPushReceived(airship, false);
            airship.getAnalytics().addEvent(new PushArrivedEvent(message));
            return;
        }

        final NotificationProvider provider = airship.getPushManager().getNotificationProvider();
        if (provider == null) {
            Logger.error("NotificationProvider is null. Unable to display notification for message: %s", message);
            notifyPushReceived(airship, false);
            airship.getAnalytics().addEvent(new PushArrivedEvent(message));
            return;
        }

        NotificationArguments arguments;
        try {
            arguments = provider.onCreateNotificationArguments(context, message);
        } catch (Exception e) {
            Logger.error(e, "Failed to generate notification arguments for message. Skipping.");
            notifyPushReceived(airship, false);
            airship.getAnalytics().addEvent(new PushArrivedEvent(message));
            return;
        }

        if (!isLongRunning && arguments.getRequiresLongRunningTask()) {
            Logger.debug("Push requires a long running task. Scheduled for a later time: %s", message);
            reschedulePush(message);
            return;
        }

        NotificationResult result;
        try {
            result = provider.onCreateNotification(context, arguments);
        } catch (Exception e) {
            Logger.error(e, "Cancelling notification display to create and display notification.");
            result = NotificationResult.cancel();
        }

        Logger.debug("IncomingPushRunnable - Received result status %s for push message: %s", result.getStatus(), message);

        switch (result.getStatus()) {
            case NotificationResult.OK:
                Notification notification = result.getNotification();
                Checks.checkNotNull(notification, "Invalid notification result. Missing notification.");

                NotificationChannelCompat notificationChannel = getNotificationChannel(airship, notification, arguments);

                // Apply legacy settings
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
                    if (notificationChannel != null) {
                        NotificationChannelUtils.applyLegacySettings(notification, notificationChannel);
                    } else {
                        applyDeprecatedSettings(airship, notification);
                    }
                }

                // Notify the provider the notification was created
                provider.onNotificationCreated(context, notification, arguments);

                // Post the notification
                boolean posted = postNotification(notification, arguments);

                airship.getAnalytics().addEvent(new PushArrivedEvent(message, notificationChannel));

                if (posted) {
                    notifyPushReceived(airship, true);
                    notifyNotificationPosted(airship, arguments);
                } else {
                    notifyPushReceived(airship, false);
                }

                break;

            case NotificationResult.CANCEL:
                airship.getAnalytics().addEvent(new PushArrivedEvent(message));
                notifyPushReceived(airship, false);
                break;

            case NotificationResult.RETRY:
                Logger.debug("Scheduling notification to be retried for a later time: %s", message);
                reschedulePush(message);
                break;
        }
    }

    /**
     * Applies deprecated sound, vibration, and quiet time settings to the notification.
     *
     * @param airship The airship instance.
     * @param notification The notification.
     */
    private void applyDeprecatedSettings(@NonNull UAirship airship, @NonNull Notification notification) {
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

    @Nullable
    private NotificationChannelCompat getNotificationChannel(@NonNull UAirship airship, @NonNull Notification notification, @NonNull NotificationArguments arguments) {
        String channelId;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            channelId = NotificationCompat.getChannelId(notification);
        } else {
            channelId = arguments.getNotificationChannelId();
        }

        return airship.getPushManager()
                      .getNotificationChannelRegistry()
                      .getNotificationChannelSync(channelId);
    }

    /**
     * Posts the notification
     *
     * @param notification The notification.
     * @param arguments The notification arguments.
     */
    private boolean postNotification(@NonNull Notification notification, @NonNull NotificationArguments arguments) {
        String tag = arguments.getNotificationTag();
        int id = arguments.getNotificationId();

        Intent contentIntent = new Intent(context, NotificationProxyActivity.class)
                .setAction(PushManager.ACTION_NOTIFICATION_RESPONSE)
                .addCategory(UUID.randomUUID().toString())
                .putExtra(PushManager.EXTRA_PUSH_MESSAGE_BUNDLE, arguments.getMessage().getPushBundle())
                .addFlags(Intent.FLAG_RECEIVER_FOREGROUND)
                .putExtra(PushManager.EXTRA_NOTIFICATION_ID, arguments.getNotificationId())
                .putExtra(PushManager.EXTRA_NOTIFICATION_TAG, arguments.getNotificationTag());

        // If the notification already has an intent, add it to the extras to be sent later
        if (notification.contentIntent != null) {
            contentIntent.putExtra(PushManager.EXTRA_NOTIFICATION_CONTENT_INTENT, notification.contentIntent);
        }

        Intent deleteIntent = new Intent(context, NotificationProxyReceiver.class)
                .setAction(PushManager.ACTION_NOTIFICATION_DISMISSED)
                .addCategory(UUID.randomUUID().toString())
                .putExtra(PushManager.EXTRA_PUSH_MESSAGE_BUNDLE, arguments.getMessage().getPushBundle())
                .putExtra(PushManager.EXTRA_NOTIFICATION_ID, arguments.getNotificationId())
                .putExtra(PushManager.EXTRA_NOTIFICATION_TAG, arguments.getNotificationTag());

        if (notification.deleteIntent != null) {
            deleteIntent.putExtra(PushManager.EXTRA_NOTIFICATION_DELETE_INTENT, notification.deleteIntent);
        }

        notification.contentIntent = PendingIntent.getActivity(context, 0, contentIntent, 0);
        notification.deleteIntent = PendingIntent.getBroadcast(context, 0, deleteIntent, 0);

        Logger.info("Posting notification: %s id: %s tag: %s", notification, id, tag);
        try {
            notificationManager.notify(tag, id, notification);

            return true;
        } catch (Exception e) {
            Logger.error(e, "Failed to post notification.");
            return false;
        }
    }

    private void notifyPushReceived(UAirship airship, boolean notificationPosted) {
        for (PushListener listener : airship.getPushManager().getPushListeners()) {
            listener.onPushReceived(message, notificationPosted);
        }
    }

    private void notifyNotificationPosted(UAirship airship, NotificationArguments arguments) {
        NotificationListener listener = airship.getPushManager().getNotificationListener();
        if (listener != null) {
            NotificationInfo info = new NotificationInfo(arguments.getMessage(), arguments.getNotificationId(), arguments.getNotificationTag());
            listener.onNotificationPosted(info);
        }
    }

    /**
     * Runs all the push actions for message.
     */
    private void runActions() {
        Bundle metadata = new Bundle();
        metadata.putParcelable(ActionArguments.PUSH_MESSAGE_METADATA, message);

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
            Logger.error("Received message callback from unexpected provider %s. Ignoring.", providerClass);
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
            Logger.debug("Ignoring push: %s", message);
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
                                 .setAction(PushManager.ACTION_DISPLAY_NOTIFICATION)
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
        @NonNull
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
        @NonNull
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
        @NonNull
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
        @NonNull
        Builder setProcessed(boolean processed) {
            isProcessed = processed;
            return this;
        }

        /**
         * Sets the notification manager.
         *
         * @param notificationManager The notification manager.
         * @return The builder instance.
         */
        @NonNull
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
        @NonNull
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
        @NonNull
        IncomingPushRunnable build() {
            Checks.checkNotNull(providerClass, "Provider class missing");
            Checks.checkNotNull(message, "Push Message missing");

            return new IncomingPushRunnable(this);
        }

    }

}
