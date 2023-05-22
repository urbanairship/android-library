package com.urbanairship.liveupdate

import android.content.Context
import androidx.core.app.NotificationCompat

/** Handlers for Live Update events. */
public sealed interface LiveUpdateHandler<T> {
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

/** Live Update handler that displays the latest content in a notification. */
public interface LiveUpdateNotificationHandler : LiveUpdateHandler<NotificationCompat.Builder> {
    /**
     * Called when a Live Update has been received.
     *
     * Implementations should return [LiveUpdateResult.ok] with a `NotificationCompat.Builder` to
     * display the Live Update in a notification, or [LiveUpdateResult.cancel] to cancel the
     * notification and stop Live Updates.
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

/** Live Update handler that allows for custom handling of Live Updates. */
public interface LiveUpdateCustomHandler : LiveUpdateHandler<Nothing>

/** Result type for [LiveUpdateHandler]s. */
public sealed class LiveUpdateResult<out T> {
    /** Successful result. */
    public class Ok<T> internal constructor(public val value: T? = null) : LiveUpdateResult<T>()

    /** Cancel result. */
    public class Cancel<T> internal constructor() : LiveUpdateResult<T>()

    public companion object {
        /**
         * Creates a new `LiveUpdateResult`, indicating that the Live Update was handled
         * successfully.
         */
        @JvmStatic
        @JvmOverloads
        public fun <T> ok(value: T? = null): LiveUpdateResult<T> = Ok(value)

        /**
         * Indicates that the Live Update should be cancelled and updates stopped.
         */
        @JvmStatic
        public fun <T> cancel(): LiveUpdateResult<T> = Cancel()
    }
}
