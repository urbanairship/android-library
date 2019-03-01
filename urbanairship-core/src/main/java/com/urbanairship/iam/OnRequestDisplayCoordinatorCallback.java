package com.urbanairship.iam;

import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

/**
 * Display coordinator callback.
 */
public interface OnRequestDisplayCoordinatorCallback {

    /**
     * Called when a message needs a display coordinator.
     *
     * @param message The message.
     * @return A display coordinator or {@code null} to use the default coordinator.
     */
    @Nullable
    DisplayCoordinator onRequestDisplayCoordinator(@NonNull InAppMessage message);

}
