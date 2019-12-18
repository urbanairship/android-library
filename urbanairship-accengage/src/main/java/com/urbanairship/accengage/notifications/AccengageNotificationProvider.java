package com.urbanairship.accengage.notifications;

import android.app.Notification;
import android.content.Context;

import com.urbanairship.push.PushMessage;
import com.urbanairship.push.notifications.NotificationArguments;
import com.urbanairship.push.notifications.NotificationProvider;
import com.urbanairship.push.notifications.NotificationResult;

import androidx.annotation.NonNull;

/**
 * Accengage notification provider.
 */
public class AccengageNotificationProvider implements NotificationProvider {

    @NonNull
    @Override
    public NotificationArguments onCreateNotificationArguments(@NonNull Context context, @NonNull PushMessage message) {
        return NotificationArguments.newBuilder(message).build();
    }

    @NonNull
    @Override
    public NotificationResult onCreateNotification(@NonNull Context context, @NonNull NotificationArguments arguments) {
        return NotificationResult.cancel();
    }

    @Override
    public void onNotificationCreated(@NonNull Context context, @NonNull Notification notification, @NonNull NotificationArguments arguments) {
        // no-op
    }

}
