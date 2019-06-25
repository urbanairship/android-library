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

    /**
     * Called when a token is updated.
     *
     * @param token The push token.
     */
    @WorkerThread
    void onPushTokenUpdated(@NonNull String token);
}
