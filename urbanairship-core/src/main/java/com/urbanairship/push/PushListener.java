package com.urbanairship.push;

import android.support.annotation.NonNull;
import android.support.annotation.WorkerThread;

/**
 * Push listener.
 */
public interface PushListener {

    /**
     * Called when a push is received.
     *
     * @param message The received push message.
     * @param notificationPosted {@code true} if a notification was posted for the push, otherwise {code false}.
     */
    @WorkerThread
    void onPushReceived(@NonNull PushMessage message, boolean notificationPosted);

}
