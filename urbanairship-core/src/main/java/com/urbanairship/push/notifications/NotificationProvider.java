/* Copyright Urban Airship and Contributors */

package com.urbanairship.push.notifications;

import android.content.Context;
import android.support.annotation.NonNull;
import android.support.annotation.WorkerThread;

import com.urbanairship.push.PushMessage;

/**
 * Used to provide notifications for Urban Airship push messages.
 */
public interface NotificationProvider {

    /**
     * Called to generate the {@link NotificationArguments} for a push message.
     *
     * @param context The context.
     * @param message The message.
     * @return The notification arguments.
     */
    @WorkerThread
    @NonNull
    NotificationArguments onCreateNotificationArguments(@NonNull Context context, @NonNull PushMessage message);

    /**
     * Called to generate the {@link NotificationResult} for a push message.
     *
     * @param context The context.
     * @param arguments The arguments from {@link #onCreateNotificationArguments(Context, PushMessage)}.
     * @return The notification result.
     */
    @WorkerThread
    @NonNull
    NotificationResult onCreateNotification(@NonNull Context context, @NonNull NotificationArguments arguments);

}
