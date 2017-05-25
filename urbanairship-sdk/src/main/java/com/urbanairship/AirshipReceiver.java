/* Copyright 2017 Urban Airship and Contributors */

package com.urbanairship;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.support.annotation.CallSuper;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

import com.urbanairship.push.PushManager;
import com.urbanairship.push.PushMessage;

/**
 * Base intent receiver to process registration and push events from Urban Airship.
 * <p/>
 * To listen for Urban Airship events, create a class that extends the AirshipReceiver.
 * Register the new class in the AndroidManifest.xml with the following intent filter:
 * <pre>
 * {@code
 * <receiver android:name="CustomAirshipReceiver" exported="false">
 * <intent-filter>
 * <action android:name="com.urbanairship.push.CHANNEL_UPDATED" />
 * <action android:name="com.urbanairship.push.OPENED" />
 * <action android:name="com.urbanairship.push.RECEIVED" />
 * <action android:name="com.urbanairship.push.DISMISSED" />
 *
 * <category android:name=}${applicationId} {@code />
 * </intent-filter>
 * </receiver>
 * }
 * </pre>
 * <p/>
 * Make sure the registered intent receiver is not exported to prevent it from receiving messages
 * outside the application.
 */
public class AirshipReceiver extends BroadcastReceiver {

    /**
     * The remote input extra.
     */
    static final String EXTRA_REMOTE_INPUT = "com.urbanairship.push.EXTRA_REMOTE_INPUT";

    /**
     * Result code indicating an activity was launched during
     * onNotificationOpened.
     */
    static final int RESULT_ACTIVITY_LAUNCHED = 1;

    /**
     * Result code indicating an activity was not launched during
     * onNotificationOpened.
     */
    static final int RESULT_ACTIVITY_NOT_LAUNCHED = -1;

    @Override
    @CallSuper
    public void onReceive(Context context, Intent intent) {
        Autopilot.automaticTakeOff(context);

        if (intent == null || intent.getAction() == null) {
            return;
        }

        String action = intent.getAction();
        Logger.debug(this.getClass().getSimpleName() + " - Received intent with action: " + action);

        switch (action) {
            case PushManager.ACTION_PUSH_RECEIVED:
                handlePushReceived(context, intent);
                break;
            case PushManager.ACTION_NOTIFICATION_OPENED:
                handlePushOpened(context, intent);
                break;
            case PushManager.ACTION_CHANNEL_UPDATED:
                handleRegistrationIntent(context, intent);
                break;
            case PushManager.ACTION_NOTIFICATION_DISMISSED:
                handleDismissedIntent(context, intent);
                break;
        }
    }

    /**
     * Handles the push received intent.
     *
     * @param context The application context.
     * @param intent The push received intent.
     */
    private void handlePushReceived(@NonNull Context context, @NonNull Intent intent) {
        PushMessage message = PushMessage.fromIntent(intent);
        if (message == null) {
            Logger.error("AirshipReceiver - Intent is missing push message for: " + intent.getAction());
            return;
        }

        boolean notificationPosted = intent.hasExtra(PushManager.EXTRA_NOTIFICATION_ID);
        onPushReceived(context, message, notificationPosted);

        if (notificationPosted) {
            int id = intent.getIntExtra(PushManager.EXTRA_NOTIFICATION_ID, -1);
            onNotificationPosted(context, new NotificationInfo(message, id));
        }
    }

    /**
     * Handles the push opened intent.
     *
     * @param context The application context.
     * @param intent The push opened intent.
     */
    private void handlePushOpened(@NonNull Context context, @NonNull Intent intent) {
        int id = intent.getIntExtra(PushManager.EXTRA_NOTIFICATION_ID, -1);
        PushMessage message = PushMessage.fromIntent(intent);
        if (message == null) {
            Logger.error("AirshipReceiver - Intent is missing push message for: " + intent.getAction());
            return;
        }

        NotificationInfo notificationInfo = new NotificationInfo(message, id);

        boolean launchedActivity;
        if (intent.hasExtra(PushManager.EXTRA_NOTIFICATION_BUTTON_ID)) {

            String buttonId = intent.getStringExtra(PushManager.EXTRA_NOTIFICATION_BUTTON_ID);
            boolean isForeground = intent.getBooleanExtra(PushManager.EXTRA_NOTIFICATION_BUTTON_FOREGROUND, false);
            Bundle remoteInput = intent.getBundleExtra(EXTRA_REMOTE_INPUT);

            ActionButtonInfo actionButtonInfo = new ActionButtonInfo(buttonId, isForeground, remoteInput);
            launchedActivity = onNotificationOpened(context, notificationInfo, actionButtonInfo);
        } else {
            launchedActivity = onNotificationOpened(context, notificationInfo);
        }

        // Only set the result if we have an ordered broadcast and the result is not already activity launched.
        if (isOrderedBroadcast() && getResultCode() != RESULT_ACTIVITY_LAUNCHED) {
            setResultCode(launchedActivity ? RESULT_ACTIVITY_LAUNCHED : RESULT_ACTIVITY_NOT_LAUNCHED);
        }
    }

    /**
     * Handles the registration intent.
     *
     * @param context The application context.
     * @param intent The registration intent.
     */
    private void handleRegistrationIntent(@NonNull Context context, @NonNull Intent intent) {
        if (intent.hasExtra(PushManager.EXTRA_ERROR)) {
            onChannelRegistrationFailed(context);
        } else {
            String channel = intent.getStringExtra(PushManager.EXTRA_CHANNEL_ID);
            if (channel == null) {
                Logger.error("AirshipReceiver - Intent is missing channel ID for: " + intent.getAction());
                return;
            }

            boolean isCreateRequest = intent.getBooleanExtra(PushManager.EXTRA_CHANNEL_CREATE_REQUEST, true);
            if (isCreateRequest) {
                onChannelCreated(context, channel);
            } else {
                onChannelUpdated(context, channel);
            }
        }
    }

    /**
     * Handles the notification dismissed intent.
     *
     * @param context The application context.
     * @param intent The notification dismissed intent.
     */
    private void handleDismissedIntent(@NonNull Context context, @NonNull Intent intent) {
        int id = intent.getIntExtra(PushManager.EXTRA_NOTIFICATION_ID, -1);

        if (!intent.hasExtra(PushManager.EXTRA_PUSH_MESSAGE_BUNDLE)) {
            Logger.error("AirshipReceiver - Intent is missing push message for: " + intent.getAction());
            return;
        }

        PushMessage message = PushMessage.fromIntent(intent);
        if (message == null) {
            Logger.error("AirshipReceiver - Intent is missing push message for: " + intent.getAction());
            return;
        }

        onNotificationDismissed(context, new NotificationInfo(message, id));
    }

    /**
     * Called when a channel ID is updated.
     *
     * @param context The application context.
     * @param channelId The channel ID.
     */
    protected void onChannelUpdated(@NonNull Context context, @NonNull String channelId){}

    /**
     * Called when a channel ID is created.
     *
     * @param context The application context.
     * @param channelId The channel ID.
     */
    protected void onChannelCreated(@NonNull Context context, @NonNull String channelId) {}

    /**
     * Called when channel registration fails.
     *
     * @param context The application context.
     */
    protected void onChannelRegistrationFailed(@NonNull Context context) {}

    /**
     * Called when a push is received.
     *
     * @param context The application context.
     * @param message The received push message.
     * @param notificationPosted {@code true} if a notification was posted for the push, otherwise {code false}. If
     * the notification was posted {@link #onNotificationPosted(Context, NotificationInfo)} will be called
     * immediately after this method with the {@link NotificationInfo}.
     */
    protected void onPushReceived(@NonNull Context context, @NonNull PushMessage message, boolean notificationPosted) {}

    /**
     * Called when a notification is posted.
     *
     * @param context The application context.
     * @param notificationInfo The notification info.
     */
    protected void onNotificationPosted(@NonNull Context context, @NonNull NotificationInfo notificationInfo) {}

    /**
     * Called when a notification is opened.
     *
     * @param context The application context.
     * @param notificationInfo The notification info.
     * @return <code>true</code> if the application was launched, otherwise <code>false</code>. If
     * <code>false</code> is returned, and {@link com.urbanairship.AirshipConfigOptions#autoLaunchApplication}
     * is enabled, the launcher activity will automatically be launched.
     */
    protected boolean onNotificationOpened(@NonNull Context context, @NonNull NotificationInfo notificationInfo) {
        return false;
    }

    /**
     * Called when a notification action button is opened.
     *
     * @param context The application context.
     * @param notificationInfo The notification info.
     * @param actionButtonInfo THe notification action button info.
     *
     * @return <code>true</code> if the application was launched, otherwise <code>false</code>. If
     * <code>false</code> is returned for a foreground notification action button,
     * and {@link com.urbanairship.AirshipConfigOptions#autoLaunchApplication} is enabled, the launcher
     * activity will automatically be launched.
     */
    protected boolean onNotificationOpened(@NonNull Context context, @NonNull NotificationInfo notificationInfo, @NonNull ActionButtonInfo actionButtonInfo) {
        return false;
    }

    /**
     * Called when a notification is dismissed.
     *
     * @param context The application context.
     * @param notificationInfo The notification info.
     */
    protected void onNotificationDismissed(@NonNull Context context, @NonNull NotificationInfo notificationInfo) {}

    /**
     * Contains information about a posted notification.
     */
    public static class NotificationInfo {
        private final PushMessage message;
        private final int notificationId;

        private NotificationInfo(@NonNull PushMessage message, int notificationId) {
            this.message = message;
            this.notificationId = notificationId;
        }

        /**
         * Returns the notification's push message.
         *
         * @return The push message.
         */
        @NonNull
        public PushMessage getMessage() {
            return message;
        }

        /**
         * Returns the notification ID.
         *
         * @return The notification ID.
         */
        public int getNotificationId() {
            return notificationId;
        }

        /**
         * Returns the notification tag.
         *
         * @return The notification tag.
         */
        @Nullable
        public String getNotificationTag() {
            return message.getNotificationTag();
        }
    }

    /**
     * Contains info about a notification action button.
     */
    public static class ActionButtonInfo {
        private final String buttonId;
        private final boolean isForeground;
        private final Bundle remoteInput;

        private ActionButtonInfo(String buttonId, boolean isForeground, Bundle remoteInput) {
            this.buttonId = buttonId;
            this.isForeground = isForeground;
            this.remoteInput = remoteInput;
        }

        /**
         * Returns the button's ID.
         *
         * @return The button's ID.
         */
        @NonNull
        public String getButtonId() {
            return buttonId;
        }

        /**
         * If the button should trigger a foreground action or not.
         *
         * @return {@code true} if the action should trigger a foreground action, otherwise {@code false}.
         */
        public boolean isForeground() {
            return isForeground;
        }

        /**
         * Remote input associated with the notification action. Only available if the action
         * button defines {@link com.urbanairship.push.notifications.LocalizableRemoteInput}
         * and the button was triggered from an Android Wear device or Android N.
         *
         * @return The remote input associated with the action button.
         */
        @Nullable
        public Bundle getRemoteInput() {
            return remoteInput;
        }
    }
}
