package com.urbanairship.push;

import androidx.annotation.NonNull;
import androidx.annotation.WorkerThread;

/**
 * Registration listener.
 */
public interface RegistrationListener {

    /**
     * Called when a channel ID is created.
     *
     * @param channelId The channel ID.
     * @deprecated Use {@link com.urbanairship.channel.AirshipChannelListener} instead. Will be removed
     * in SDK 13.0.
     */
    @Deprecated
    @WorkerThread
    void onChannelCreated(@NonNull String channelId);

    /**
     * Called when a channel ID is updated.
     *
     * @param channelId The channel ID.
     * @deprecated Use {@link com.urbanairship.channel.AirshipChannelListener} instead. Will be removed
     * in SDK 13.0.
     */
    @WorkerThread
    void onChannelUpdated(@NonNull String channelId);

    /**
     * Called when a token is updated.
     *
     * @param token The push token.
     */
    @WorkerThread
    void onPushTokenUpdated(@NonNull String token);
}
