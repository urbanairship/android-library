/* Copyright Airship and Contributors */

package com.urbanairship.push.notifications;

import android.content.Context;

import com.urbanairship.UAirship;

import androidx.annotation.NonNull;
import androidx.core.app.NotificationCompat;

/**
 * Notification builder extender to add UA notification action buttons to a
 * notification.
 */
public class ActionsNotificationExtender implements NotificationCompat.Extender {

    private final Context context;
    private final NotificationArguments arguments;

    /**
     * ActionsNotificationExtender default constructor.
     *
     * @param context The application context.
     * @param arguments The notification arguments.
     */
    public ActionsNotificationExtender(@NonNull Context context, @NonNull NotificationArguments arguments) {
        this.context = context.getApplicationContext();
        this.arguments = arguments;
    }

    @NonNull
    @Override
    public NotificationCompat.Builder extend(@NonNull NotificationCompat.Builder builder) {
        String group = arguments.getMessage().getInteractiveNotificationType();

        NotificationActionButtonGroup actionGroup = UAirship.shared().getPushManager().getNotificationActionGroup(group);
        if (actionGroup == null) {
            return builder;
        }

        for (NotificationCompat.Action action : actionGroup.createAndroidActions(context, arguments, arguments.getMessage().getInteractiveActionsPayload())) {
            builder.addAction(action);
        }

        return builder;
    }

}
