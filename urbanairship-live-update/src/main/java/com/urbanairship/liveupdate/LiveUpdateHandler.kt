package com.urbanairship.liveupdate

import android.app.Notification
import android.content.Context
import androidx.core.app.NotificationCompat
import java.util.Objects

/**
 * @hide
 */
public sealed interface BaseLiveUpdateHandler

/** Handlers for Live Update events. */
@Deprecated(
    message = "Deprecated in favor of Suspend and Callback versions of LiveUpdateHandlers"
)
public sealed interface LiveUpdateHandler<T> : BaseLiveUpdateHandler {
    /**
     * Called when a Live Update has been received.
     *
     * @return The [LiveUpdateResult].
     */
    public fun onUpdate(
        /** Application `Context`. */
        context: Context,
        /** The Live Update [event][LiveUpdateEvent]. */
        event: LiveUpdateEvent,
        /** The Live Update data. */
        update: LiveUpdate
    ): LiveUpdateResult<T>
}

/**
 * Live Update handler that displays the latest content in a notification.
 *
 * @deprecated Replace with [SuspendLiveUpdateNotificationHandler] or [CallbackLiveUpdateNotificationHandler].
 */
@Deprecated(
    message = "Deprecated in favor of SuspendLiveUpdateNotificationHandler and CallbackLiveUpdateNotificationHandler"
)
public interface LiveUpdateNotificationHandler : LiveUpdateHandler<NotificationCompat.Builder> {
    /**
     * Called when a Live Update has been received.
     *
     * Implementations should return [LiveUpdateResult.ok] with a `NotificationCompat.Builder` to
     * display the Live Update in a notification, or [LiveUpdateResult.cancel] to cancel the
     * notification and end Live Updates.
     *
     * An `ok` result with a `null` value will be ignored and will neither update nor cancel the
     * existing notification.
     *
     * @return The [LiveUpdateResult].
     */
    public override fun onUpdate(
        /** Application `Context`. */
        context: Context,
        /** The Live Update [event][LiveUpdateEvent]. */
        event: LiveUpdateEvent,
        /** The Live Update data. */
        update: LiveUpdate
    ): LiveUpdateResult<NotificationCompat.Builder>
}

/** Async handlers for Live Update events. */
public sealed interface AsyncLiveUpdateNotificationHandler : BaseLiveUpdateHandler

/**
 * Live Update handler that displays the latest content in a notification and uses a suspend
 * function to handle updates.
 */
public abstract class SuspendLiveUpdateNotificationHandler : AsyncLiveUpdateNotificationHandler {
    /**
     * Called when a Live Update has been received.
     *
     * @return The [LiveUpdateResult].
     */
    public abstract suspend fun onUpdate(
        /** Application `Context`. */
        context: Context,
        /** The Live Update [event][LiveUpdateEvent]. */
        event: LiveUpdateEvent,
        /** The Live Update data. */
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
public interface CallbackLiveUpdateNotificationHandler : AsyncLiveUpdateNotificationHandler {
    public fun onUpdate(
        /** Application `Context`. */
        context: Context,
        /** The Live Update [event][LiveUpdateEvent]. */
        event: LiveUpdateEvent,
        /** The Live Update data. */
        update: LiveUpdate,
        /** The callback. */
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

    /** Result type for [LiveUpdateResultCallback]s. */
    public class NotificationResult(
        /** The notification tag. */
        public val notificationTag: String,
        /** The notification ID. */
        public val notificationId: Int,
        /** The notification. */
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

/** Live Update handler that allows for custom handling of Live Updates. */
// TODO(async-live-updates): deprecate this and replace with callback and suspend versions
@Deprecated(
    message = "Deprecated in favor of TODO!"
)
public interface LiveUpdateCustomHandler : LiveUpdateHandler<Nothing>

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
