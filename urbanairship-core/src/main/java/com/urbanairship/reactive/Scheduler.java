/* Copyright Airship and Contributors */

package com.urbanairship.reactive;

import androidx.annotation.NonNull;
import androidx.annotation.RestrictTo;

/**
 * Interface for scheduling runnables for use with Observable
 *
 * @hide
 */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
public interface Scheduler {

    /**
     * Schedules a runnable.
     *
     * @param runnable The runnable.
     * @return A Subscription.
     */
    @NonNull
    Subscription schedule(@NonNull Runnable runnable);

    /**
     * Schedules a runnable to be executed after a delay
     *
     * @param delayTimeMs The delay time in milliseconds.
     * @param runnable The runnable.
     * @return A Subscription.
     */
    @NonNull
    Subscription schedule(long delayTimeMs, @NonNull Runnable runnable);

}
