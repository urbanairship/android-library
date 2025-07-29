package com.urbanairship.push

import androidx.annotation.WorkerThread

/**
 * Push listener.
 */
public fun interface PushListener {

    /**
     * Called when a push is received.
     *
     * @param message The received push message.
     * @param notificationPosted `true` if a notification was posted for the push, otherwise {code false}.
     */
    @WorkerThread
    public fun onPushReceived(message: PushMessage, notificationPosted: Boolean)
}
