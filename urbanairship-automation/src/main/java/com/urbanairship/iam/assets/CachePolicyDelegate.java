/* Copyright Airship and Contributors */

package com.urbanairship.iam.assets;

import androidx.annotation.NonNull;

import com.urbanairship.iam.InAppMessageSchedule;

/**
 * Delegate used to determine the caching for a {@link InAppMessageSchedule}.
 */
public interface CachePolicyDelegate {

    /**
     * Called to determine if the assets for a message should be cached on schedule.
     *
     * @param schedule The schedule.
     * @return {@code true} to cache, otherwise {@code false}.
     */
    boolean shouldCacheOnSchedule(@NonNull InAppMessageSchedule schedule);

    /**
     * Called to determine if the cache should be cleared for a schedule after the
     * message is finished displaying. The cache is always cleared if the schedule is
     * cancelled, has reached its executing limit, or expires.
     *
     * @param schedule The schedule.
     * @return {@code true} to clear the cache, otherwise {@code false}.
     */
    boolean shouldPersistCacheAfterDisplay(@NonNull InAppMessageSchedule schedule);

}
