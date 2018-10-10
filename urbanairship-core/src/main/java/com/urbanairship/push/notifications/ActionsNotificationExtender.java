/* Copyright 2018 Urban Airship and Contributors */

package com.urbanairship.push.notifications;

import android.content.Context;
import android.support.annotation.NonNull;
import android.support.v4.app.NotificationCompat;

import com.urbanairship.UAirship;
import com.urbanairship.push.PushMessage;

/**
 * Notification builder extender to add UA notification action buttons to a
 * notification.
 */
public class ActionsNotificationExtender implements NotificationCompat.Extender {

    private final PushMessage message;
    private final Context context;
    private final int notificationId;

    /**
     * ActionsNotificationExtender default constructor.
     *
     * @param context The application context.
     * @param message The push message.
     * @param notificationId The notification ID.
     */
    public ActionsNotificationExtender(@NonNull Context context, @NonNull PushMessage message, int notificationId) {
        this.context = context.getApplicationContext();
        this.message = message;
        this.notificationId = notificationId;
    }

    @NonNull
    @Override
    public NotificationCompat.Builder extend(@NonNull NotificationCompat.Builder builder) {
        NotificationActionButtonGroup actionGroup = UAirship.shared().getPushManager().getNotificationActionGroup(message.getInteractiveNotificationType());
        if (actionGroup == null) {
            return builder;
        }

        for (NotificationCompat.Action action : actionGroup.createAndroidActions(context, message, notificationId, message.getInteractiveActionsPayload())) {
            builder.addAction(action);
        }

        return builder;
    }
}
