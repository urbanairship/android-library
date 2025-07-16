/* Copyright Airship and Contributors */
package com.urbanairship.channel

import androidx.annotation.WorkerThread

/**
 * Channel listener.
 */
public interface AirshipChannelListener {

    /**
     * Called when a channel ID is created.
     *
     * @param channelId The channel ID.
     */
    @WorkerThread
    public fun onChannelCreated(channelId: String)
}
