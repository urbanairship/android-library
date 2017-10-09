/* Copyright 2017 Urban Airship and Contributors */

package com.urbanairship.reactive;

import android.support.annotation.RestrictTo;

import com.urbanairship.Cancelable;

/**
 * Subscription implementation for Observables.
 *
 * @hide
 */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
public class Subscription {

    private Runnable runnable;
    private boolean canceled = false;

    public Subscription() {}

    /**
     * Subscription constructor
     * @param runnable A runnable to execute on disposal.
     */
    Subscription(Runnable runnable) {
        this.runnable = runnable;
    }

    /**
     * Creates a new Subscription that executes the provided runnable when disposed.
     * @param runnable The runnable
     * @return A Subscription.
     */
    public static Subscription create(final Runnable runnable) {
        return new Subscription(runnable);
    }

    /**
     * Creates a new Subscription with no side effects.
     * @return A Subscription.
     */
    public static Subscription empty() {
        return new Subscription();
    }

    /**
     * Cancels the Subscription.
     */
    public synchronized void cancel() {
        if (this.runnable != null) {
            this.runnable.run();
        }
        this.canceled = true;
    }

    /**
     * Checks whether the Subscription is cancelled.
     * @return <code>true</code> if the Subscription is cancelled, <code>false</code> otherwise.
     */
    public synchronized boolean isCancelled() {
        return this.canceled;
    }
}
