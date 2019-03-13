package com.urbanairship.push.notifications;

import android.app.NotificationManager;
import android.content.Context;
import android.os.Build;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

import com.urbanairship.Logger;

/**
 * Notification channel utils.
 */
class NotificationChannelUtils {

    /**
     * Returns the provided channel if it exists or the default channel.
     *
     * @param context The context.
     * @param channelId The notification channel.
     * @param defaultChannel The default notification channel.
     * @return The channelId if it exists, or the default channel.
     */
    @NonNull
    static String getActiveChannel(Context context, @Nullable String channelId, @NonNull String defaultChannel) {
        if (channelId == null) {
            return defaultChannel;
        }

        if (defaultChannel.equals(channelId)) {
            return channelId;
        }

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            return channelId;
        } else {
            NotificationManager notificationManager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
            if (notificationManager.getNotificationChannel(channelId) != null) {
                return channelId;
            }

            Logger.error("Notification channel %s does not exist. Falling back to %s", channelId, defaultChannel);
            return defaultChannel;
        }
    }

}
