/* Copyright Airship and Contributors */

package com.urbanairship.iam;

import androidx.annotation.NonNull;

/**
 * Listener for in-app message display events.
 */
public interface InAppMessageListener {

    /**
     * Called when an in-app message is displayed.
     *
     * @param scheduleId The schedule ID.
     * @param message The in-app message.
     */
    void onMessageDisplayed(@NonNull String scheduleId, @NonNull InAppMessage message);

    /**
     * Called when an in-app message finished displaying.
     *
     * @param scheduleId The schedule ID.
     * @param message The in-app message.
     * @param resolutionInfo The resolution info.
     */
    void onMessageFinished(@NonNull String scheduleId, @NonNull InAppMessage message, @NonNull ResolutionInfo resolutionInfo);

}
