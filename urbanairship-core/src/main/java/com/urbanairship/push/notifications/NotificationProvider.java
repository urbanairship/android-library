/* Copyright Urban Airship and Contributors */

package com.urbanairship.push.notifications;

import android.app.Notification;
import android.content.Context;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.annotation.RestrictTo;
import android.support.annotation.WorkerThread;
import android.support.v4.app.NotificationManagerCompat;

import com.urbanairship.Logger;
import com.urbanairship.R;
import com.urbanairship.UAirship;
import com.urbanairship.push.PushMessage;

/**
 * Used to provide notifications for Urban Airship push messages.
 *
 * The notification provider should never post the notification to the notification manager. The Urban
 * Airship SDK will do that on behave of the application.
 */
public abstract class NotificationProvider {

    /**
     * Default notification channel ID.
     */
    @NonNull
    public static final String DEFAULT_NOTIFICATION_CHANNEL = "com.urbanairship.default";

    /**
     * Called to generate the {@link NotificationArguments} for a push message.
     *
     * @param context The context.
     * @param message The message.
     * @return The notification arguments.
     */
    @WorkerThread
    @NonNull
    public abstract NotificationArguments onCreateNotificationArguments(@NonNull Context context, @NonNull PushMessage message);

    /**
     * Called to generate the {@link NotificationResult} for a push message.
     *
     * @param context The context.
     * @param arguments The arguments from {@link #onCreateNotificationArguments(Context, PushMessage)}.
     * @return The notification result.
     */
    @WorkerThread
    @NonNull
    public abstract NotificationResult onCreateNotification(@NonNull Context context, @NonNull NotificationArguments arguments);

    /**
     * Called before posting the notification.
     *
     * The notification will have settings applied from an associated {@link NotificationChannelCompat} on pre-O devices
     *
     * Use this method to apply any global overrides to the notification.
     *
     * @param context The context.
     * @param notification The notification.
     * @param arguments The notification arguments.
     */
    @WorkerThread
    public void onNotificationCreated(@NonNull Context context, @NonNull Notification notification, @NonNull NotificationArguments arguments) {}

}
