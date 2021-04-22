/* Copyright Airship and Contributors */

package com.urbanairship.accengage.notifications;

import android.app.Notification;
import android.content.Context;

import com.urbanairship.AirshipConfigOptions;
import com.urbanairship.Logger;
import com.urbanairship.UAirship;
import com.urbanairship.accengage.AccengageMessage;
import com.urbanairship.app.GlobalActivityMonitor;
import com.urbanairship.push.PushMessage;
import com.urbanairship.push.notifications.NotificationArguments;
import com.urbanairship.push.notifications.NotificationChannelUtils;
import com.urbanairship.push.notifications.NotificationProvider;
import com.urbanairship.push.notifications.NotificationResult;

import androidx.annotation.NonNull;
import androidx.core.app.NotificationCompat;

/**
 * Accengage notification provider.
 */
public class AccengageNotificationProvider implements NotificationProvider {

    private final AirshipConfigOptions configOptions;

    public AccengageNotificationProvider(@NonNull AirshipConfigOptions configOptions) {
        this.configOptions = configOptions;
    }

    /**
     * Deprecated. Use {@link AccengageNotificationProvider(AirshipConfigOptions)} instead.
     */
    @Deprecated
    public AccengageNotificationProvider() {
        this.configOptions = UAirship.shared().getAirshipConfigOptions();
    }

    @NonNull
    @Override
    public NotificationArguments onCreateNotificationArguments(@NonNull Context context, @NonNull PushMessage message) {
        AccengageMessage accengageMessage = AccengageMessage.fromAirshipPushMessage(message);

        String requestedChannelId = accengageMessage.getAccengageChannel();
        String activeChannelId = NotificationChannelUtils.getActiveChannel(requestedChannelId, DEFAULT_NOTIFICATION_CHANNEL);

        return NotificationArguments.newBuilder(message)
                .setNotificationChannelId(activeChannelId)
                .setNotificationId(String.valueOf(accengageMessage.getAccengageSystemId()), accengageMessage.getAccengageSystemId())
                .build();
    }

    @NonNull
    @Override
    public NotificationResult onCreateNotification(@NonNull Context context, @NonNull NotificationArguments arguments) {
        AccengageMessage message = AccengageMessage.fromAirshipPushMessage(arguments.getMessage());

        if (!message.getAccengageForeground() && GlobalActivityMonitor.shared(context).isAppForegrounded()) {
            Logger.debug("Received Accengage Push message but application was in foreground, skip it...");
            return NotificationResult.cancel();
        }

        Logger.debug("Push message received from Accengage, displaying notification...");

        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, arguments.getNotificationChannelId())
                .extend(new AccengageNotificationExtender(context, configOptions, message, arguments));

        builder = onExtendBuilder(context, builder, message, arguments);

        Notification notification = builder.build();
        return NotificationResult.notification(notification);
    }

    /**
     * Override this method to extend the notification builder.
     *
     * @param context The context.
     * @param builder The builder.
     * @param message The Accengage message.
     * @param arguments The notification arguments.
     * @return The notification builder.
     */
    @NonNull
    protected NotificationCompat.Builder onExtendBuilder(@NonNull Context context,
                                                         @NonNull NotificationCompat.Builder builder,
                                                         @NonNull AccengageMessage message,
                                                         @NonNull NotificationArguments arguments) {
        return builder;
    }

    @Override
    public void onNotificationCreated(@NonNull Context context, @NonNull Notification notification, @NonNull NotificationArguments arguments) {
        // no-op
    }
}
