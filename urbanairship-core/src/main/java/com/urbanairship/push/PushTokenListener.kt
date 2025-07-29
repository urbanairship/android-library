/* Copyright Airship and Contributors */
package com.urbanairship.push

import androidx.annotation.WorkerThread

/**
 * Push token listener.
 */
public fun interface PushTokenListener {

    /**
     * Called when a token is updated.
     *
     * @param token The push token.
     */
    @WorkerThread
    public fun onPushTokenUpdated(token: String)
}
