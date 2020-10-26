/* Copyright Airship and Contributors */

package com.urbanairship.channel;

import androidx.annotation.Nullable;
import androidx.annotation.WorkerThread;

/**
 * Named user listener.
 */
public interface NamedUserListener {

    /**
     * Called when the named user ID changes.
     *
     * @param id The named user ID.
     */
    @WorkerThread
    void onNamedUserIdChanged(@Nullable String id);
}
