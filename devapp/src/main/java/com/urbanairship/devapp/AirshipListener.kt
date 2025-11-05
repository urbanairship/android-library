/* Copyright Airship and Contributors */
package com.urbanairship.devapp

import android.util.Log
import com.urbanairship.channel.AirshipChannelListener
import com.urbanairship.push.NotificationActionButtonInfo
import com.urbanairship.push.NotificationInfo
import com.urbanairship.push.NotificationListener
import com.urbanairship.push.PushListener
import com.urbanairship.push.PushMessage
import com.urbanairship.push.PushTokenListener

/**
 * Listener for push, notifications, and registrations events.
 */
class AirshipListener : PushListener, NotificationListener, PushTokenListener,
    AirshipChannelListener {

    override fun onNotificationPosted(notificationInfo: NotificationInfo) {
        Log.i(TAG, "Notification posted: $notificationInfo")
    }

    override fun onNotificationOpened(notificationInfo: NotificationInfo): Boolean {
        Log.i(TAG, "Notification opened: $notificationInfo")

        // Return false here to allow Airship to auto launch the launcher
        // activity for foreground notification action buttons
        return false
    }

    override fun onNotificationForegroundAction(
        notificationInfo: NotificationInfo, actionButtonInfo: NotificationActionButtonInfo
    ): Boolean {
        Log.i(TAG, "Notification action: $notificationInfo $actionButtonInfo")

        // Return false here to allow Airship to auto launch the launcher
        // activity for foreground notification action buttons
        return false
    }

    override fun onNotificationBackgroundAction(
        notificationInfo: NotificationInfo, actionButtonInfo: NotificationActionButtonInfo
    ) {
        Log.i(TAG, "Notification action: $notificationInfo $actionButtonInfo")
    }

    override fun onNotificationDismissed(notificationInfo: NotificationInfo) {
        Log.i(
            TAG,
            "Notification dismissed. Alert: " + notificationInfo.message.alert + ". Notification ID: " + notificationInfo.notificationId
        )
    }

    override fun onPushReceived(message: PushMessage, notificationPosted: Boolean) {
        Log.i(
            TAG,
            "Received push message. Alert: " + message.alert + ". Posted notification: " + notificationPosted
        )
    }

    override fun onChannelCreated(channelId: String) {
        Log.i(TAG, "Channel created $channelId")
    }

    override fun onPushTokenUpdated(token: String) {
        Log.i(TAG, "Push token updated $token")
    }

    companion object {
        private const val TAG = "AirshipListener"
    }
}
