/* Copyright Airship and Contributors */

package com.urbanairship.iam.assets;

import com.urbanairship.iam.InAppMessage;

import androidx.annotation.NonNull;

/**
 * Delegate used to determine the caching for a {@link InAppMessage}.
 */
public interface CachePolicyDelegate {

    /**
     * Called to determine if the assets for a message should be cached on schedule.
     *
     * @param scheduleId The schedule Id.
     * @param message The in-app message.
     * @return {@code true} to cache, otherwise {@code false}.
     */
    boolean shouldCacheOnSchedule(@NonNull String scheduleId, @NonNull InAppMessage message);

    /**
     * Called to determine if the cache should be cleared for an in-app message after the
     * message is finished displaying. The cache is always cleared if the schedule is
     * cancelled, has reached its executing limit, or expires.
     *
     * @param scheduleId The schedule Id.
     * @param message The in-app message.
     * @return {@code true} to clear the cache, otherwise {@code false}.
     */
    boolean shouldPersistCacheAfterDisplay(@NonNull String scheduleId, @NonNull InAppMessage message);

}
