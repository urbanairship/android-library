/* Copyright 2017 Urban Airship and Contributors */

package com.urbanairship.reactive;

import android.os.Handler;
import android.os.Looper;
import android.support.annotation.NonNull;
import android.support.annotation.RestrictTo;

import com.urbanairship.Cancelable;

/**
 * Scheduler implementations
 *
 * @hide
 */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
public class Schedulers {

    /**
     * Creates a a scheduler that targets the current looper at schedule time.
     *
     * @return A Scheduler.
     */
    public static CurrentLooper currentLooper() {
        return new CurrentLooper();
    }

    /**
     * Creates a Scheduler that targets the provided looper at scheduler time.
     *
     * @param looper The looper.
     * @return A Scheduler
     */
    public static RunLoop runLoop(Looper looper) {
        return new RunLoop(looper);
    }

    /**
     * Abstract Scheduler base class.
     */
    public static abstract class Base implements Scheduler {
        public Cancelable schedule(final Runnable runnable) {
            final Cancelable subscription = Subscription.empty();

            new Handler(getLooper()).post(new Runnable() {
                @Override
                public void run() {
                    if (!subscription.isCancelled()) {
                        runnable.run();
                    }
                }
            });

            return subscription;
        }

        public Cancelable schedule(final Runnable runnable, long delayTimeMs) {
            final Cancelable subscription = Subscription.empty();

            new Handler(getLooper()).postDelayed(new Runnable() {
                @Override
                public void run() {
                    if (!subscription.isCancelled()) {
                        runnable.run();
                    }
                }
            }, delayTimeMs);

            return subscription;
        }

        abstract Looper getLooper();
    }

    /**
     * Scheduler that targets the current looper at schedule time.
     */
    public static class CurrentLooper extends Base {
        @Override
        Looper getLooper() {
            return Looper.myLooper();
        }
    }

    /**
     * Scheduler that targets a specific looper at scheduler time.
     */
    public static class RunLoop extends Base {
        private Looper looper;

        /**
         * Run loop Scheduler constructor.
         * @param looper The looper to scheduler on.
         */
        RunLoop(@NonNull Looper looper) {
            if (looper == null) {
                throw new IllegalArgumentException("Looper cannot be null");
            }
            this.looper = looper;
        }

        @Override
        Looper getLooper(){
            return looper;
        }
    }
}
