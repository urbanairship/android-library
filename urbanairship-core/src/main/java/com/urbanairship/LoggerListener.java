/* Copyright Urban Airship and Contributors */

package com.urbanairship;

import android.support.annotation.Nullable;

/**
 * Listener interface for the Urban Airship logger.
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
