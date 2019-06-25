package com.urbanairship.push.notifications;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.urbanairship.push.PushMessage;

/**
 * Arguments used to create a notification in the {@link NotificationProvider}.
 */
public class NotificationArguments {

    private int notificationId;
    private boolean longRunningTask;
    private String notificationChannelId;
    private String notificationTag;

    private PushMessage message;

    private NotificationArguments(@NonNull Builder builder) {
        this.notificationId = builder.notificationId;
        this.notificationChannelId = builder.notificationChannelId;
        this.longRunningTask = builder.longRunningTask;
        this.message = builder.message;
        this.notificationTag = builder.notificationTag;
    }

    /**
     * Gets the Id that will be used to post the notification.
     *
     * @return The notification Id.
     */
    public int getNotificationId() {
        return notificationId;
    }

    /**
     * Gets the tag that will be used to post the notification.
     *
     * @return The notification tag.
     */
    @Nullable
    public String getNotificationTag() {
        return notificationTag;
    }

    /**
     * Gets the notification channel Id.
     *
     * @return The notification channel Id.
     */
    @NonNull
    public String getNotificationChannelId() {
        return notificationChannelId;
    }

    /**
     * Gets the flag indicating the notification requires a long running task.
     *
     * @return {@code true} if the notification requires a long running task, otherwise {@code false}.
     */
    public boolean getRequiresLongRunningTask() {
        return longRunningTask;
    }

    /**
     * Gets the push message.
     *
     * @return The push message.
     */
    @NonNull
    public PushMessage getMessage() {
        return message;
    }

    /**
     * Factory method to create a new builder.
     *
     * @return A builder.
     */
    @NonNull
    public static Builder newBuilder(@NonNull PushMessage message) {
        return new Builder(message);
    }

    /**
     * Arguments builder.
     */
    public static class Builder {

        private int notificationId = -1;
        private boolean longRunningTask;
        private String notificationChannelId = NotificationProvider.DEFAULT_NOTIFICATION_CHANNEL;
        private String notificationTag;
        private PushMessage message;

        private Builder(@NonNull PushMessage message) {
            this.message = message;
        }

        /**
         * Sets the notification tag and Id.
         *
         * @param notificationTag The notification tag.
         * @param notificationId The notification Id.
         * @return The builder instance.
         */
        public Builder setNotificationId(@Nullable String notificationTag, int notificationId) {
            this.notificationTag = notificationTag;
            this.notificationId = notificationId;
            return this;
        }

        /**
         * Sets the notification channel Id.
         *
         * @param notificationChannelId The notification channel Id.
         * @return The builder instance.
         */
        public Builder setNotificationChannelId(@NonNull String notificationChannelId) {
            this.notificationChannelId = notificationChannelId;
            return this;
        }

        /**
         * Sets if the notification requires a long running task to generate the notification.
         *
         * If {@code true}, the push message will be scheduled to process at a later time when the
         * app has more background time. If {@code false}, the app has approximately 10 seconds to
         * generate the notification.
         * <p>
         * Apps that make use of this feature are highly encouraged to add {@code RECEIVE_BOOT_COMPLETED} so
         * the push message will persist between device reboots.
         *
         * @param longRunningTask {@code true} to require a long running task, otherwise {@code false}.
         * @return The builder instance.
         */
        public Builder setRequiresLongRunningTask(boolean longRunningTask) {
            this.longRunningTask = longRunningTask;
            return this;
        }

        /**
         * Builds the notification arguments.
         *
         * @return The notification arguments.
         */
        public NotificationArguments build() {
            return new NotificationArguments(this);
        }

    }

}
