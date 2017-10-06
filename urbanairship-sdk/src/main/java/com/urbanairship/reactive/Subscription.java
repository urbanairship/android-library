/* Copyright 2017 Urban Airship and Contributors */

package com.urbanairship.reactive;

import android.support.annotation.RestrictTo;

import com.urbanairship.Cancelable;

/**
 * Cancelable implementation for Observables.
 *
 * @hide
 */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
public class Subscription implements Cancelable {

    private Runnable runnable;
    private boolean canceled = false;

    Subscription() {}

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
    public static Cancelable create(final Runnable runnable) {
        return new Subscription(runnable);
    }

    /**
     * Creates a new Subscription with no side effects.
     * @return A Subscription.
     */
    public static Cancelable empty() {
        return new Subscription();
    }

    @Override
    synchronized
    public boolean cancel(boolean mayInterruptIfRunning) {
        if (this.runnable != null) {
            this.runnable.run();
        }
        this.canceled = true;
        return true;
    }

    @Override
    synchronized
    public boolean cancel() {
        return cancel(true);
    }

    @Override
    synchronized
    public boolean isDone() {
        return this.runnable == null ? true : this.canceled;
    }

    @Override
    synchronized
    public boolean isCancelled() {
        return this.canceled;
    }
}
