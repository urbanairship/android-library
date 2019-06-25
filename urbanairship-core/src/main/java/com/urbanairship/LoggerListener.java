/* Copyright Airship and Contributors */

package com.urbanairship;

import androidx.annotation.Nullable;

/**
 * Listener interface for the Airship logger.
 */
public interface LoggerListener {

    /**
     * Called when a message is logged.
     *
     * @param priority The log priority.
     * @param throwable An optional throwable.
     * @param message The message.
     */
    void onLog(int priority, @Nullable Throwable throwable, @Nullable String message);

}
