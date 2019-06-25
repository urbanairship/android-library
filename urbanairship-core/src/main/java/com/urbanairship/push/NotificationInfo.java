package com.urbanairship.push;

import android.content.Intent;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RestrictTo;
import androidx.annotation.VisibleForTesting;

/**
 * Notification info.
 */
public class NotificationInfo {

    private PushMessage message;
    private int notificationId;
    private String notificationTag;

    /**
     * Default constructor.
     *
     * @param message The message.
     * @param notificationId The notification Id.
     * @param notificationTag The notification tag.
     *
     * @hide
     */
    @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
    @VisibleForTesting
    public NotificationInfo(PushMessage message, int notificationId, String notificationTag) {
        this.message = message;
        this.notificationTag = notificationTag;
        this.notificationId = notificationId;
    }

    @Nullable
    static NotificationInfo fromIntent(Intent intent) {
        PushMessage message = PushMessage.fromIntent(intent);
        if (message == null) {
            return null;
        }

        int id = intent.getIntExtra(PushManager.EXTRA_NOTIFICATION_ID, -1);
        String tag = intent.getStringExtra(PushManager.EXTRA_NOTIFICATION_TAG);
        return new NotificationInfo(message, id, tag);
    }

    /**
     * Returns the notification's push message.
     *
     * @return The push message.
     */
    @NonNull
    public PushMessage getMessage() {
        return message;
    }

    /**
     * Returns the notification's Id.
     *
     * @return The notification's Id.
     */
    public int getNotificationId() {
        return notificationId;
    }

    /**
     * Returns the notification's tag.
     *
     * @return The notification's tag.
     */
    @Nullable
    public String getNotificationTag() {
        return notificationTag;
    }

    @NonNull
    @Override
    public String toString() {
        return "NotificationInfo{" +
                "alert=" + message.getAlert() +
                ", notificationId=" + notificationId +
                ", notificationTag='" + notificationTag + '\'' +
                '}';
    }

}
