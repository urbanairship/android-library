/* Copyright Airship and Contributors */
package com.urbanairship.push.notifications

import android.app.Notification
import android.content.Context
import androidx.annotation.WorkerThread
import com.urbanairship.push.PushMessage

/**
 * Used to provide notifications for Airship push messages.
 *
 * The notification provider should never post the notification to the notification manager. The Urban
 * Airship SDK will do that on behave of the application.
 */
public interface NotificationProvider {

    /**
     * Called to generate the [NotificationArguments] for a push message.
     *
     * @param context The context.
     * @param message The message.
     * @return The notification arguments.
     */
    @WorkerThread
    public fun onCreateNotificationArguments(context: Context, message: PushMessage): NotificationArguments

    /**
     * Called to generate the [NotificationResult] for a push message.
     *
     * @param context The context.
     * @param arguments The arguments from [.onCreateNotificationArguments].
     * @return The notification result.
     */
    @WorkerThread
    public fun onCreateNotification(context: Context, arguments: NotificationArguments): NotificationResult

    /**
     * Called before posting the notification.
     *
     * The notification will have settings applied from an associated [NotificationChannelCompat] on pre-O devices
     *
     * Use this method to apply any global overrides to the notification.
     *
     * @param context The context.
     * @param notification The notification.
     * @param arguments The notification arguments.
     */
    @WorkerThread
    public fun onNotificationCreated(
        context: Context,
        notification: Notification,
        arguments: NotificationArguments
    )

    public companion object {
        /**
         * Default notification channel ID.
         */
        public const val DEFAULT_NOTIFICATION_CHANNEL: String = "com.urbanairship.default"
    }
}
