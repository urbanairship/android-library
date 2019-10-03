/* Copyright Airship and Contributors */

package com.urbanairship.channel;

import androidx.annotation.NonNull;
import androidx.annotation.WorkerThread;

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
    void onChannelCreated(@NonNull String channelId);

    /**
     * Called when a channel ID is updated.
     *
     * @param channelId The channel ID.
     */
    @WorkerThread
    void onChannelUpdated(@NonNull String channelId);
}
