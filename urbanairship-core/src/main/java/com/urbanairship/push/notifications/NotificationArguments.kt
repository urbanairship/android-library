package com.urbanairship.push.notifications

import com.urbanairship.push.PushMessage

/**
 * Arguments used to create a notification in the [NotificationProvider].
 */
public class NotificationArguments private constructor(builder: Builder) {

    /**
     * The Id that will be used to post the notification.
     */
    @JvmField
    public val notificationId: Int = builder.notificationId

    /**
     * The flag indicating the notification requires a long running task.
     */
    public val requiresLongRunningTask: Boolean = builder.longRunningTask

    /**
     * The notification channel Id.
     */
    @JvmField
    public val notificationChannelId: String = builder.notificationChannelId

    /**
     * The tag that will be used to post the notification.
     */
    @JvmField
    public val notificationTag: String? = builder.notificationTag

    /**
     * The push message.
     */
    @JvmField
    public val message: PushMessage = builder.message

    /**
     * Arguments builder.
     */
    public class Builder(public val message: PushMessage) {

        internal var notificationId: Int = -1
        public var longRunningTask: Boolean = false
            private set
        public var notificationChannelId: String = NotificationProvider.DEFAULT_NOTIFICATION_CHANNEL
            private set
        public var notificationTag: String? = null
            private set

        /**
         * Sets the notification tag and Id.
         *
         * @param notificationTag The notification tag.
         * @param notificationId The notification Id.
         * @return The builder instance.
         */
        public fun setNotificationId(notificationTag: String?, notificationId: Int): Builder {
            return this.also {
                it.notificationId = notificationId
                it.notificationTag = notificationTag
            }
        }

        /**
         * Sets the notification channel Id.
         *
         * @param notificationChannelId The notification channel Id.
         * @return The builder instance.
         */
        public fun setNotificationChannelId(notificationChannelId: String): Builder {
            return this.also { it.notificationChannelId = notificationChannelId }
        }

        /**
         * Sets if the notification requires a long running task to generate the notification.
         *
         * If `true`, the push message will be scheduled to process at a later time when the
         * app has more background time. If `false`, the app has approximately 10 seconds to
         * generate the notification.
         *
         *
         * Apps that make use of this feature are highly encouraged to add `RECEIVE_BOOT_COMPLETED` so
         * the push message will persist between device reboots.
         *
         * @param longRunningTask `true` to require a long running task, otherwise `false`.
         * @return The builder instance.
         */
        public fun setRequiresLongRunningTask(longRunningTask: Boolean): Builder {
            return this.also { it.longRunningTask = longRunningTask }
        }

        /**
         * Builds the notification arguments.
         *
         * @return The notification arguments.
         */
        public fun build(): NotificationArguments {
            return NotificationArguments(this)
        }
    }

    public companion object {

        /**
         * Factory method to create a new builder.
         *
         * @return A builder.
         */
        @JvmStatic
        public fun newBuilder(message: PushMessage): Builder {
            return Builder(message)
        }
    }
}
