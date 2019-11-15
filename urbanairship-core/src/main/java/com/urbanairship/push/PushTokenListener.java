/* Copyright Airship and Contributors */

package com.urbanairship.push;

import androidx.annotation.NonNull;
import androidx.annotation.WorkerThread;

/**
 * Push token listener.
 */
public interface PushTokenListener {

    /**
     * Called when a token is updated.
     *
     * @param token The push token.
     */
    @WorkerThread
    void onPushTokenUpdated(@NonNull String token);
}
