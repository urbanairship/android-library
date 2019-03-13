/* Copyright Urban Airship and Contributors */

package com.urbanairship.push.notifications;

import android.app.NotificationManager;
import android.content.Context;
import android.os.Build;
import android.support.annotation.NonNull;
import android.support.annotation.RestrictTo;

import com.urbanairship.Logger;
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
        String channelId = message.getNotificationChannel(factory.getNotificationChannel());
        channelId = NotificationChannelUtils.getActiveChannel(context, channelId, NotificationArguments.DEFAULT_NOTIFICATION_CHANNEL);
        return NotificationArguments.newBuilder(message)
                                    .setNotificationChannelId(channelId)
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
}
