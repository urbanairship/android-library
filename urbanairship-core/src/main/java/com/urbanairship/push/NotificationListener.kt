package com.urbanairship.push

import androidx.annotation.MainThread
import androidx.annotation.WorkerThread

/**
 * Notification listener.
 */
public interface NotificationListener {

    /**
     * Called when a notification is posted.
     *
     * @param notificationInfo The notification info.
     */
    @WorkerThread
    public fun onNotificationPosted(notificationInfo: NotificationInfo)

    /**
     * Called when the notification is opened.
     *
     * @param notificationInfo The notification info.
     * @return `true` if the application was launched, otherwise `false`. If
     * `false` is returned, and [com.urbanairship.AirshipConfigOptions.autoLaunchApplication]
     * is enabled, the launcher activity will automatically be launched. The push message will be available
     * in the launcher intent's extras. Use [PushMessage.fromIntent] to access the message.
     */
    @MainThread
    public fun onNotificationOpened(notificationInfo: NotificationInfo): Boolean

    /**
     * Called when a foreground notification action button is tapped.
     *
     * @param notificationInfo The notification info.
     * @return `true` if the application was launched, otherwise `false`. If
     * `false` is returned, and [com.urbanairship.AirshipConfigOptions.autoLaunchApplication]
     * is enabled, the launcher activity will automatically be launched. The push message will be available
     * in the launcher intent's extras. Use [PushMessage.fromIntent] to access the message.
     */
    @MainThread
    public fun onNotificationForegroundAction(
        notificationInfo: NotificationInfo, actionButtonInfo: NotificationActionButtonInfo
    ): Boolean

    /**
     * Called when a background notification action button is tapped.
     *
     * @param notificationInfo The notification info.
     */
    @MainThread
    public fun onNotificationBackgroundAction(
        notificationInfo: NotificationInfo, actionButtonInfo: NotificationActionButtonInfo
    )

    /**
     * Called when a notification is dismissed.
     *
     * @param notificationInfo The notification info.
     */
    @MainThread
    public fun onNotificationDismissed(notificationInfo: NotificationInfo)
}
