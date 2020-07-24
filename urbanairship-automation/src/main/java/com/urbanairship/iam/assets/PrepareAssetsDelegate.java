/* Copyright Airship and Contributors */

package com.urbanairship.iam.assets;

import com.urbanairship.iam.InAppMessage;

import androidx.annotation.NonNull;

/**
 * Listener used by {@link AssetManager} to populate assets for in-app messages.
 */
public interface PrepareAssetsDelegate {

    /**
     * Called to prepare assets for the in-app message before the message is displayed.
     *
     * @param scheduleId The scheduleId.
     * @param message The message. This might be different then schedule's message if the message
     * was extended.
     * @param assets The assets.
     * @return The prepare result.
     */
    @AssetManager.PrepareResult
    int onPrepare(@NonNull String scheduleId, @NonNull InAppMessage message, @NonNull Assets assets);

    /**
     * Called to prepare assets for the in-app message when the message is scheduled. If any assets
     * fail to download, {@link #onPrepare(String, InAppMessage, Assets)} will still
     * be called before the message is displayed.
     *
     * @param scheduleId The scheduleId.
     * @param message The message. This might be different then schedule's message if the message was extended.
     * @param assets The assets.
     */
    void onSchedule(@NonNull String scheduleId, @NonNull InAppMessage message, @NonNull Assets assets);

}
