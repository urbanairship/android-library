package com.urbanairship.push;

import android.content.Intent;
import androidx.annotation.MainThread;
import androidx.annotation.NonNull;
import androidx.annotation.WorkerThread;

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
    void onNotificationPosted(@NonNull NotificationInfo notificationInfo);

    /**
     * Called when the notification is opened.
     *
     * @param notificationInfo The notification info.
     * @return <code>true</code> if the application was launched, otherwise <code>false</code>. If
     * <code>false</code> is returned, and {@link com.urbanairship.AirshipConfigOptions#autoLaunchApplication}
     * is enabled, the launcher activity will automatically be launched. The push message will be available
     * in the launcher intent's extras. Use {@link PushMessage#fromIntent(Intent) to access the message.
     */
    @MainThread
    boolean onNotificationOpened(@NonNull NotificationInfo notificationInfo);

    /**
     * Called when a background notification action button is tapped.
     *
     * @param notificationInfo The notification info.
     * @return <code>true</code> if the application was launched, otherwise <code>false</code>. If
     * <code>false</code> is returned, and {@link com.urbanairship.AirshipConfigOptions#autoLaunchApplication}
     * is enabled, the launcher activity will automatically be launched. The push message will be available
     * in the launcher intent's extras. Use {@link PushMessage#fromIntent(Intent) to access the message
     */
    @MainThread
    boolean onNotificationForegroundAction(@NonNull NotificationInfo notificationInfo, @NonNull NotificationActionButtonInfo actionButtonInfo);

    /**
     * Called when a background notification action button is tapped.
     *
     * @param notificationInfo The notification info.
     */
    @MainThread
    void onNotificationBackgroundAction(@NonNull NotificationInfo notificationInfo, @NonNull NotificationActionButtonInfo actionButtonInfo);

    /**
     * Called when a notification is dismissed.
     *
     * @param notificationInfo The notification info.
     */
    @MainThread
    void onNotificationDismissed(@NonNull NotificationInfo notificationInfo);

}
