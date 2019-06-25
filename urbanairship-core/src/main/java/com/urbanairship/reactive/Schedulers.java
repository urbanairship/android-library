/* Copyright Airship and Contributors */

package com.urbanairship.reactive;

import android.os.Handler;
import android.os.Looper;
import androidx.annotation.NonNull;
import androidx.annotation.RestrictTo;

/**
 * Scheduler implementations
 *
 * @hide
 */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
public class Schedulers {

    private static LooperScheduler main;

    /**
     * Creates a Scheduler that targets the provided looper at scheduler time.
     *
     * @param looper The looper to schedule on.
     * @return A Scheduler.
     */
    @NonNull
    public static LooperScheduler looper(@NonNull Looper looper) {
        return new LooperScheduler(looper);
    }

    /**
     * Gets the scheduler that targets the main looper.
     *
     * @return A Scheduler.
     */
    @NonNull
    public static LooperScheduler main() {
        if (main == null) {
            main = looper(Looper.getMainLooper());
        }

        return main;
    }

    /**
     * Scheduler that targets a specific RunLoop at schedule time.
     */
    public static class LooperScheduler implements Scheduler {

        private final Looper looper;

        /**
         * Run loop Scheduler constructor.
         *
         * @param looper The looper to scheduler on.
         */
        public LooperScheduler(@NonNull Looper looper) {
            this.looper = looper;
        }

        @NonNull
        public Subscription schedule(@NonNull final Runnable runnable) {
            final Subscription subscription = Subscription.empty();

            new Handler(looper).post(new Runnable() {
                @Override
                public void run() {
                    if (!subscription.isCancelled()) {
                        runnable.run();
                    }
                }
            });

            return subscription;
        }

        @NonNull
        public Subscription schedule(long delayTimeMs, @NonNull final Runnable runnable) {
            final Subscription subscription = Subscription.empty();

            new Handler(looper).postDelayed(new Runnable() {
                @Override
                public void run() {
                    if (!subscription.isCancelled()) {
                        runnable.run();
                    }
                }
            }, delayTimeMs);

            return subscription;
        }

    }

}
