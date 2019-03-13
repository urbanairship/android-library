package com.urbanairship.push.notifications;

import android.app.Notification;
import android.app.NotificationManager;
import android.content.Context;
import android.os.Build;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.annotation.RestrictTo;
import android.support.annotation.WorkerThread;
import android.support.v4.app.NotificationManagerCompat;

import com.urbanairship.Logger;
import com.urbanairship.UAirship;

/**
 * Notification channel utils.
 * @hide
 */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
public class NotificationChannelUtils {

    /**
     * Returns the provided channel if it exists or the default channel.
     *
     * @param channelId The notification channel.
     * @param defaultChannel The default notification channel.
     * @return The channelId if it exists, or the default channel.
     */
    @NonNull
    @WorkerThread
    static String getActiveChannel(@Nullable String channelId, @NonNull String defaultChannel) {
        if (channelId == null) {
            return defaultChannel;
        }

        if (defaultChannel.equals(channelId)) {
            return channelId;
        }

        if (UAirship.shared().getPushManager().getNotificationChannelRegistry().getNotificationChannelSync(channelId) == null) {
            Logger.error("Notification channel %s does not exist. Falling back to %s", channelId, defaultChannel);
            return defaultChannel;
        }

        return channelId;
    }

    static int priorityForImportance(int importance) {
        switch (importance) {
            case NotificationManagerCompat.IMPORTANCE_DEFAULT:
                return Notification.PRIORITY_DEFAULT;
            case NotificationManagerCompat.IMPORTANCE_HIGH:
                return Notification.PRIORITY_HIGH;
            case NotificationManagerCompat.IMPORTANCE_LOW:
                return Notification.PRIORITY_LOW;
            case NotificationManagerCompat.IMPORTANCE_MAX:
                return Notification.PRIORITY_MAX;
            case NotificationManagerCompat.IMPORTANCE_MIN:
                return Notification.PRIORITY_MIN;
            default:
                return Notification.PRIORITY_LOW;
        }
    }

    public static void applyLegacySettings(@NonNull Notification notification, @NonNull NotificationChannelCompat channelCompat) {
        notification.priority = priorityForImportance(channelCompat.getImportance());

        // If it's not higher than default importance the notification disable sound, light, and vibration
        if (channelCompat.getImportance() < NotificationManagerCompat.IMPORTANCE_DEFAULT) {
            notification.vibrate = null;
            notification.sound = null;
            notification.defaults = 0;
            return;
        }

        if (channelCompat.getSound() != null) {
            notification.sound = channelCompat.getSound();
            notification.defaults |= Notification.DEFAULT_SOUND;
        }

        if (channelCompat.shouldShowLights()) {
            notification.ledARGB = channelCompat.getLightColor();
            notification.defaults |= Notification.DEFAULT_LIGHTS;
        }

        if (channelCompat.shouldVibrate()) {
            notification.vibrate = channelCompat.getVibrationPattern();
            notification.defaults |= Notification.DEFAULT_VIBRATE;
        }
    }

}
