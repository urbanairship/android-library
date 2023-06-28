/* Copyright Airship and Contributors */
package com.urbanairship.push

import androidx.annotation.MainThread

/**
 * Airship push notification status listener.
 */
public fun interface PushNotificationStatusListener {

    /**
     * Called when the status changes.
     * @param status The current status.
     */
    @MainThread
    public fun onChange(status: PushNotificationStatus)
}
