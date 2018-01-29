/* Copyright 2018 Urban Airship and Contributors */

package com.urbanairship.reactive;

import android.support.annotation.RestrictTo;

import com.urbanairship.Cancelable;

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
    Subscription schedule(Runnable runnable);

    /**
     * Schedules a runnable to be executed after a delay
     *
     * @param runnable The runnable.
     * @param delayTimeMs The delay time in milliseconds.
     * @return A Subscription.
     */
    Subscription schedule(Runnable runnable, long delayTimeMs);
}
