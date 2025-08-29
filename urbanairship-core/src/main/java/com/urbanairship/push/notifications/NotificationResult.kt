package com.urbanairship.push.notifications

import android.app.Notification

/**
 * Results for [NotificationProvider.onCreateNotification].
 */
public class NotificationResult private constructor(
    @JvmField public val notification: Notification?,
    status: Status
) {

    public enum class Status {
        /**
         * Indicates that a Notification was successfully created.
         */
        OK,

        /**
         * Indicates that a Notification was not successfully created and that a job should be
         * scheduled to retry later.
         */
        RETRY,

        /**
         * Indicates that a Notification was not successfully created and no work should be scheduled
         * to retry.
         */
        CANCEL
    }

    /**
     * Gets the status.
     */
    @JvmField
    public val status: Status

    /**
     * NotificationFactory.Result constructor.
     *
     * @param notification The Notification.
     * @param status The status.
     */
    init {
        if (notification == null && status == Status.OK) {
            this.status = Status.CANCEL
        } else {
            this.status = status
        }
    }

    public companion object {
        /**
         * Creates a new result with a notification and a [Status.OK] status code.
         *
         * @param notification The notification.
         * @return An instance of [NotificationResult].
         */
        @JvmStatic
        public fun notification(notification: Notification): NotificationResult {
            return NotificationResult(notification, Status.OK)
        }

        /**
         * Creates a new result with a [Status.CANCEL] status code.
         *
         * @return An instance of [NotificationResult].
         */
        @JvmStatic
        public fun cancel(): NotificationResult {
            return NotificationResult(null, Status.CANCEL)
        }

        /**
         * Creates a new result with a [Status.RETRY] status code.
         *
         * @return An instance of [NotificationResult].
         */
        @JvmStatic
        public fun retry(): NotificationResult {
            return NotificationResult(null, Status.RETRY)
        }
    }
}
