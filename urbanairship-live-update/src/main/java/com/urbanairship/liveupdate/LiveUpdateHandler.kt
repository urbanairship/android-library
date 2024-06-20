package com.urbanairship.liveupdate

import android.app.Notification
import android.content.Context
import androidx.core.app.NotificationCompat
import java.util.Objects

/** Base interface for Live Update handlers. */
public sealed interface LiveUpdateHandler

/** Handler for Live Update events that update a Notification. */
public sealed interface NotificationLiveUpdateHandler : LiveUpdateHandler

/** Handler for Live Update events that allows for custom handling. */
public sealed interface CustomLiveUpdateHandler : LiveUpdateHandler

/**
 * Live Update handler that displays the latest content in a notification and uses a suspend
 * function to handle updates.
 */
public abstract class SuspendLiveUpdateNotificationHandler : NotificationLiveUpdateHandler {
    /**
     * Called when a Live Update has been received.
     *
     * @param context Application `Context`.
     * @param event The Live Update [event][LiveUpdateEvent].
     * @param update The Live Update data.
     * @return The [LiveUpdateResult].
     */
    public abstract suspend fun onUpdate(
        context: Context,
        event: LiveUpdateEvent,
        update: LiveUpdate,
    ): LiveUpdateResult<NotificationCompat.Builder>

    protected fun LiveUpdateResult.Ok<NotificationCompat.Builder>.extend(
        callback: LiveUpdateResult.NotificationExtender
    ): LiveUpdateResult.Ok<NotificationCompat.Builder> = this.apply {
        extender = callback
    }
}

/**
 * Live Update handler that displays the latest content in a notification and uses a callback
 * to return update results.
 */
public interface CallbackLiveUpdateNotificationHandler : NotificationLiveUpdateHandler {
    /**
     * Called when a Live Update has been received.
     *
     * @param context Application `Context`.
     * @param event The Live Update [event][LiveUpdateEvent].
     * @param update The Live Update data.
     * @param resultCallback The callback.
     */
    public fun onUpdate(
        context: Context,
        event: LiveUpdateEvent,
        update: LiveUpdate,
        resultCallback: LiveUpdateResultCallback
    )

    /** Live Update Result callbacks. */
    public interface LiveUpdateResultCallback {
        /**
         * Indicates that the Live Update was handled successfully and a notification should be
         * posted using the provided [builder].
         *
         * @param builder The notification builder.
         * @return The [NotificationResult] or `null` if the notification could not be built/posted.
         */
        public fun ok(builder: NotificationCompat.Builder): NotificationResult?

        /**
         * Indicates that the Live Update should be cancelled and updates ended.
         */
        public fun cancel()
    }

    /**
     * Result type for [LiveUpdateResultCallback]s.
     *
     * @property notificationTag The notification tag.
     * @property notificationId The notification ID.
     * @property notification The notification.
     */
    public class NotificationResult(
        public val notificationTag: String,
        public val notificationId: Int,
        public val notification: Notification
    ) {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false

            other as NotificationResult

            if (notificationTag != other.notificationTag) return false
            if (notificationId != other.notificationId) return false
            if (notification != other.notification) return false

            return true
        }

        override fun hashCode(): Int {
            return Objects.hash(notificationTag, notificationId, notification)
        }
    }
}

/**
 * Live Update handler that allows for custom handling of Live Updates and uses a suspend
 * function to handle updates.
 */
public interface SuspendLiveUpdateCustomHandler : CustomLiveUpdateHandler {

    /**
     * Called when a Live Update has been received.
     *
     * @param context Application `Context`.
     * @param event The Live Update [event][LiveUpdateEvent].
     * @param update The Live Update data.
     * @return The [LiveUpdateResult].
     */
    public suspend fun onUpdate(
        context: Context,
        event: LiveUpdateEvent,
        update: LiveUpdate,
    ): LiveUpdateResult<Nothing>
}

/**
 * Live Update handler that allows for custom handling of Live Updates and uses a callback
 * to return update results.
 */
public interface CallbackLiveUpdateCustomHandler : CustomLiveUpdateHandler {

    /**
     * Called when a Live Update has been received.
     *
     * @param context Application `Context`.
     * @param event The Live Update [event][LiveUpdateEvent].
     * @param update The Live Update data.
     * @param resultCallback The callback.
     */
    public fun onUpdate(
        context: Context,
        event: LiveUpdateEvent,
        update: LiveUpdate,
        resultCallback: LiveUpdateResultCallback
    )

    /** Live Update Result callbacks. */
    public interface LiveUpdateResultCallback {
        /**
         * Indicates that the Live Update was handled successfully.
         */
        public fun ok()

        /**
         * Indicates that the Live Update should be cancelled and updates ended.
         */
        public fun cancel()
    }
}

/** Result type for [LiveUpdateHandler]s. */
public sealed class LiveUpdateResult<out T> {
    /** Successful result. */
    public class Ok<T> internal constructor(
        public val value: T? = null,
    ) : LiveUpdateResult<T>() {
        internal var extender: NotificationExtender? = null

        public fun extend(
            extender: NotificationExtender
        ): Ok<T> = this.apply {
            this.extender = extender
        }
    }

    /** Cancel result. */
    public class Cancel<T> internal constructor() : LiveUpdateResult<T>()

    /** Provides access to the built `Notification`. */
    public interface NotificationExtender {
        public fun extend(
            notification: Notification,
            notificationId: Int,
            notificationTag: String
        )
    }

    public companion object {
        /**
         * Creates a new `LiveUpdateResult`, indicating that the Live Update was handled
         * successfully.
         */
        @JvmStatic
        @JvmOverloads
        public fun <T> ok(
            value: T? = null,
        ): Ok<T> = Ok(value)

        /**
         * Indicates that the Live Update should be cancelled and updates ended.
         */
        @JvmStatic
        public fun <T> cancel(): Cancel<T> = Cancel()
    }
}
