/*
Copyright 2009-2016 Urban Airship Inc. All rights reserved.

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

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.support.annotation.CallSuper;
import android.support.annotation.NonNull;

import com.urbanairship.Autopilot;
import com.urbanairship.Logger;

/**
 * Base intent receiver to process registration and push events from Urban Airship.
 * <p/>
 * To listen for Urban Airship events, create a class that extends the BaseIntentReceiver.
 * Register the new class in the AndroidManifest.xml with the following intent filter:
 * <pre>
 * {@code
 <receiver android:name="CustomIntentReceiver" exported="false">
    <intent-filter>
        <action android:name="com.urbanairship.push.CHANNEL_UPDATED" />
        <action android:name="com.urbanairship.push.OPENED" />
        <action android:name="com.urbanairship.push.RECEIVED" />
        <action android:name="com.urbanairship.push.DISMISSED" />

        <category android:name=}${applicationId} {@code />
    </intent-filter>
 </receiver>
 * }
 * </pre>
 * <p/>
 * Make sure the registered intent receiver is not exported to prevent it from receiving messages
 * outside the application.
 */
public abstract class BaseIntentReceiver extends BroadcastReceiver {

    /**
     * Result code indicating an activity was launched during
     * {@link #onNotificationActionOpened(android.content.Context, PushMessage, int, String, boolean)}
     * or {@link #onNotificationOpened(android.content.Context, PushMessage, int)}.
     */
    public static final int RESULT_ACTIVITY_LAUNCHED = 1;

    /**
     * Result code indicating an activity was not launched during
     * {@link #onNotificationActionOpened(android.content.Context, PushMessage, int, String, boolean)}
     * or {@link #onNotificationOpened(android.content.Context, PushMessage, int)}.
     */
    public static final int RESULT_ACTIVITY_NOT_LAUNCHED = -1;

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
            Logger.error("BaseIntentReceiver - Intent is missing push message for: " + intent.getAction());
            return;
        }

        if (intent.hasExtra(PushManager.EXTRA_NOTIFICATION_ID)) {
            int id = intent.getIntExtra(PushManager.EXTRA_NOTIFICATION_ID, -1);
            onPushReceived(context, message, id);
        } else {
            onBackgroundPushReceived(context, message);
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
            Logger.error("BaseIntentReceiver - Intent is missing push message for: " + intent.getAction());
            return;
        }

        boolean launchedActivity;
        if (intent.hasExtra(PushManager.EXTRA_NOTIFICATION_BUTTON_ID)) {
            String buttonId = intent.getStringExtra(PushManager.EXTRA_NOTIFICATION_BUTTON_ID);
            boolean isForeground = intent.getBooleanExtra(PushManager.EXTRA_NOTIFICATION_BUTTON_FOREGROUND, false);
            launchedActivity = onNotificationActionOpened(context, message, id, buttonId, isForeground);
        } else {
            launchedActivity = onNotificationOpened(context, message, id);
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
                Logger.error("BaseIntentReceiver - Intent is missing channel ID for: " + intent.getAction());
                return;
            }
            onChannelRegistrationSucceeded(context, channel);
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
            Logger.error("BaseIntentReceiver - Intent is missing push message for: " + intent.getAction());
            return;
        }

        PushMessage message = new PushMessage(intent.getBundleExtra(PushManager.EXTRA_PUSH_MESSAGE_BUNDLE));

        onNotificationDismissed(context, message, id);
    }

    /**
     * Called when channel registration succeeded.
     *
     * @param context The application context.
     * @param channelId The channel ID.
     */
    protected abstract void onChannelRegistrationSucceeded(@NonNull Context context, @NonNull String channelId);

    /**
     * Called when channel registration fails.
     *
     * @param context The application context.
     */
    protected abstract void onChannelRegistrationFailed(@NonNull Context context);

    /**
     * Called when a push is received.
     *
     * @param context The application context.
     * @param message The received push message.
     * @param notificationId The notification ID of the message posted in the notification center.
     */
    protected abstract void onPushReceived(@NonNull Context context, @NonNull PushMessage message, int notificationId);

    /**
     * Called when a push is received that did not result in a notification being posted.
     *
     * @param context The application context.
     * @param message The received push message.
     */
    protected abstract void onBackgroundPushReceived(@NonNull Context context, @NonNull PushMessage message);

    /**
     * Called when a notification is opened.
     *
     * @param context The application context.
     * @param message The push message associated with the notification.
     * @param notificationId The notification ID.
     * @return <code>true</code> if the application was launched, otherwise <code>false</code>. If
     * <code>false</code> is returned, and {@link com.urbanairship.AirshipConfigOptions#autoLaunchApplication}
     * is enabled, the launcher activity will automatically be launched.
     */
    protected abstract boolean onNotificationOpened(@NonNull Context context, @NonNull PushMessage message, int notificationId);

    /**
     * Called when a notification action button is opened.
     *
     * @param context The application context.
     * @param message The push message associated with the notification.
     * @param notificationId The notification ID.
     * @param buttonId The button identifier.
     * @param isForeground If the notification action button is foreground or not. If <code>false</code> the application
     * should not be launched.
     * @return <code>true</code> if the application was launched, otherwise <code>false</code>. If
     * <code>false</code> is returned for a foreground notification action button,
     * and {@link com.urbanairship.AirshipConfigOptions#autoLaunchApplication} is enabled, the launcher
     * activity will automatically be launched.
     */
    protected abstract boolean onNotificationActionOpened(@NonNull Context context, @NonNull PushMessage message, int notificationId, @NonNull String buttonId, boolean isForeground);

    /**
     * Called when a notification is dismissed from either swiping away the notification or from
     * clearing all notifications.
     *
     * @param context The application context.
     * @param message The push message associated with the notification.
     * @param notificationId The notification ID.
     */
    protected void onNotificationDismissed(@NonNull Context context, @NonNull PushMessage message, int notificationId) {}
}
