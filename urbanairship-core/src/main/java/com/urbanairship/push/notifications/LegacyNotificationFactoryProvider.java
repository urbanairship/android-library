/* Copyright Airship and Contributors */

package com.urbanairship.push.notifications;

import android.app.Notification;
import android.content.Context;
import androidx.annotation.NonNull;
import androidx.annotation.RestrictTo;

import com.urbanairship.push.PushMessage;

/**
 * Wraps a deprecated factory in a notification provider.
 *
 * @hide
 */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
public class LegacyNotificationFactoryProvider implements NotificationProvider {

    private final NotificationFactory factory;

    /**
     * Default constructor.
     *
     * @param factory The notification factory.
     */
    public LegacyNotificationFactoryProvider(NotificationFactory factory) {
        this.factory = factory;
    }

    @NonNull
    @Override
    public NotificationArguments onCreateNotificationArguments(@NonNull Context context, @NonNull PushMessage message) {
        String requestedChannelId = message.getNotificationChannel(factory.getNotificationChannel());
        String activeChannelId = NotificationChannelUtils.getActiveChannel(requestedChannelId, DEFAULT_NOTIFICATION_CHANNEL);

        return NotificationArguments.newBuilder(message)
                                    .setNotificationChannelId(activeChannelId)
                                    .setNotificationId(message.getNotificationTag(), factory.getNextId(message))
                                    .setRequiresLongRunningTask(factory.requiresLongRunningTask(message))
                                    .build();
    }

    @NonNull
    @Override
    public NotificationResult onCreateNotification(@NonNull Context context, @NonNull NotificationArguments arguments) {
        NotificationFactory.Result result = factory.createNotificationResult(arguments.getMessage(), arguments.getNotificationId(), arguments.getRequiresLongRunningTask());

        switch (result.getStatus()) {
            case NotificationFactory.Result.OK:
                if (result.getNotification() != null) {
                    return NotificationResult.notification(result.getNotification());
                } else {
                    return NotificationResult.cancel();
                }

            case NotificationFactory.Result.RETRY:
                return NotificationResult.retry();

            case NotificationFactory.Result.CANCEL:
            default:
                return NotificationResult.cancel();
        }
    }

    @Override
    public void onNotificationCreated(@NonNull Context context, @NonNull Notification notification, @NonNull NotificationArguments arguments) {}

}
