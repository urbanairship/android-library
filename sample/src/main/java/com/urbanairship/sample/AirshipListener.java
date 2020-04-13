/* Copyright Airship and Contributors */

package com.urbanairship.sample;

import android.util.Log;

import com.urbanairship.channel.AirshipChannelListener;
import com.urbanairship.push.NotificationActionButtonInfo;
import com.urbanairship.push.NotificationInfo;
import com.urbanairship.push.NotificationListener;
import com.urbanairship.push.PushListener;
import com.urbanairship.push.PushMessage;
import com.urbanairship.push.PushTokenListener;

import androidx.annotation.NonNull;

/**
 * Listener for push, notifications, and registrations events.
 */
public class AirshipListener implements PushListener, NotificationListener, PushTokenListener, AirshipChannelListener {

    private static final String TAG = "AirshipListener";

    @Override
    public void onNotificationPosted(@NonNull NotificationInfo notificationInfo) {
        Log.i(TAG, "Notification posted: " + notificationInfo);
    }

    @Override
    public boolean onNotificationOpened(@NonNull NotificationInfo notificationInfo) {
        Log.i(TAG, "Notification opened: " + notificationInfo);

        // Return false here to allow Airship to auto launch the launcher
        // activity for foreground notification action buttons
        return false;
    }

    @Override
    public boolean onNotificationForegroundAction(@NonNull NotificationInfo notificationInfo, @NonNull NotificationActionButtonInfo actionButtonInfo) {
        Log.i(TAG, "Notification action: " + notificationInfo + " " + actionButtonInfo);

        // Return false here to allow Airship to auto launch the launcher
        // activity for foreground notification action buttons
        return false;
    }

    @Override
    public void onNotificationBackgroundAction(@NonNull NotificationInfo notificationInfo, @NonNull NotificationActionButtonInfo actionButtonInfo) {
        Log.i(TAG, "Notification action: " + notificationInfo + " " + actionButtonInfo);
    }

    @Override
    public void onNotificationDismissed(@NonNull NotificationInfo notificationInfo) {
        Log.i(TAG, "Notification dismissed. Alert: " + notificationInfo.getMessage().getAlert() + ". Notification ID: " + notificationInfo.getNotificationId());
    }

    @Override
    public void onPushReceived(@NonNull PushMessage message, boolean notificationPosted) {
        Log.i(TAG, "Received push message. Alert: " + message.getAlert() + ". Posted notification: " + notificationPosted);
    }

    @Override
    public void onChannelCreated(@NonNull String channelId) {
        Log.i(TAG, "Channel created " + channelId);
    }

    @Override
    public void onChannelUpdated(@NonNull String channelId) {
        Log.i(TAG, "Channel updated " + channelId);
    }

    @Override
    public void onPushTokenUpdated(@NonNull String token) {
        Log.i(TAG, "Push token updated " + token);
    }

}
