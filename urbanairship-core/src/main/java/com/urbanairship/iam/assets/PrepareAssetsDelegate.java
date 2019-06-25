/* Copyright Airship and Contributors */

package com.urbanairship.iam.assets;

import androidx.annotation.NonNull;

import com.urbanairship.iam.InAppMessage;
import com.urbanairship.iam.InAppMessageSchedule;

/**
 * Listener used by {@link AssetManager} to populate assets for in-app messages.
 */
public interface PrepareAssetsDelegate {

    /**
     * Called to prepare assets for the in-app message before the message is displayed.
     *
     * @param schedule The schedule.
     * @param message The message. This might be different then schedule's message if the message
     * was extended.
     * @param assets The assets.
     * @return The prepare result.
     */
    @AssetManager.PrepareResult
    int onPrepare(@NonNull InAppMessageSchedule schedule, @NonNull InAppMessage message, @NonNull Assets assets);

    /**
     * Called to prepare assets for the in-app message when the message is scheduled. If any assets
     * fail to download, {@link #onPrepare(InAppMessageSchedule, InAppMessage, Assets)} will still
     * be called before the message is displayed.
     *
     * @param schedule The schedule.
     * @param message The message. This might be different then schedule's message if the message was extended.
     * @param assets The assets.
     */
    void onSchedule(@NonNull InAppMessageSchedule schedule, @NonNull InAppMessage message, @NonNull Assets assets);

}
