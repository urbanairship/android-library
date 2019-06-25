/* Copyright Airship and Contributors */

package com.urbanairship.util;

import android.os.Handler;
import android.os.SystemClock;
import androidx.annotation.IntDef;
import androidx.annotation.NonNull;
import androidx.annotation.RestrictTo;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.Executor;

/**
 * Executor that supports retrying operations when
 * using {@link #execute(Operation)} to submit operations.
 *
 * @hide
 */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
public class RetryingExecutor implements Executor {

    /**
     * Initial retry backoff
     */
    private static final long INITIAL_BACKOFF_MILLIS = 30000; // 30 seconds

    /**
     * Max backoff
     */
    private static final long MAX_BACKOFF_MILLIS = 300000; // 5 minutes

    @IntDef({ RESULT_FINISHED, RESULT_RETRY, RESULT_CANCEL })
    @Retention(RetentionPolicy.SOURCE)
    public @interface Result {}

    /**
     * When executing an {@link Operation}, it notifies the executor that the operation
     * is finished.
     */
    public static final int RESULT_FINISHED = 0;

    /**
     * When executing an {@link Operation}, it notifies the executor that the operation
     * needs to be retried.
     */
    public static final int RESULT_RETRY = 1;

    /**
     * When executing an {@link Operation}, it notifies the executor that the operation
     * is finished and any dependent operations should be skipped.
     */
    public static final int RESULT_CANCEL = 2;

    private final Handler scheduler;
    private final Executor executor;

    private boolean isPaused = false;
    private final List<Runnable> pendingRunnables = new ArrayList<>();

    /**
     * Default constructor.
     *
     * @param scheduler A handler used to schedule retries.
     * @param executor The executor that performs the operations.
     */
    public RetryingExecutor(@NonNull Handler scheduler, @NonNull Executor executor) {
        this.scheduler = scheduler;
        this.executor = executor;
    }

    /**
     * Executes a runnable. The runnable will not be retried.
     *
     * @param runnable The runnable
     */
    @Override
    public void execute(@NonNull final Runnable runnable) {
        execute(new Operation() {
            @Override
            public int run() {
                runnable.run();
                return RESULT_FINISHED;
            }
        });
    }

    /**
     * Executes a single operation.
     *
     * @param operation The operation to execute.
     */
    public void execute(@NonNull Operation operation) {
        execute(operation, INITIAL_BACKOFF_MILLIS);
    }

    /**
     * Executes a list of operations in order.
     *
     * @param operations The operations to execute.
     */
    public void execute(@NonNull Operation... operations) {
        execute(new ChainedOperations(Arrays.asList(operations)));
    }

    /**
     * Helper method that handles executing an operation.
     *
     * @param operation The operation.
     * @param backOff The next backOff if retrying the operation.
     */
    private void execute(final @NonNull Operation operation, final long backOff) {
        final Runnable executeRunnable = new Runnable() {
            @Override
            public void run() {
                synchronized (pendingRunnables) {
                    if (isPaused) {
                        pendingRunnables.add(this);
                        return;
                    }
                }

                int result = operation.run();
                if (result == RESULT_RETRY) {
                    scheduler.postAtTime(new Runnable() {
                        @Override
                        public void run() {
                            execute(operation, Math.min(backOff * 2, MAX_BACKOFF_MILLIS));
                        }
                    }, executor, SystemClock.uptimeMillis() + backOff);
                }

            }
        };

        executor.execute(executeRunnable);
    }

    /**
     * Pauses/resumes the executor. When paused, the scheduler will continue to run, but
     * operations/runnables will not execute until resumed.
     *
     * @param isPaused {@code true} to pause, {@code false} to resume.
     */
    public void setPaused(boolean isPaused) {
        if (isPaused == this.isPaused) {
            return;
        }

        synchronized (pendingRunnables) {
            this.isPaused = isPaused;

            if (!this.isPaused && !pendingRunnables.isEmpty()) {
                List<Runnable> copy = new ArrayList<>(this.pendingRunnables);
                this.pendingRunnables.clear();

                for (Runnable runnable : copy) {
                    executor.execute(runnable);
                }
            }
        }
    }

    /**
     * Operation that is able to be retried.
     */
    public interface Operation {

        /**
         * Called to run the operation.
         *
         * @return {@link #RESULT_RETRY} to retry or {@link #RESULT_FINISHED} to finish the operation.
         */
        @Result
        int run();

    }

    /**
     * Operation that runs a list of operations in order. If any of the operations
     * cancels, the rest of the operations will be cancelled.
     */
    private class ChainedOperations implements Operation {

        private final List<? extends Operation> operations;

        ChainedOperations(@NonNull List<? extends Operation> operations) {
            this.operations = new ArrayList<>(operations);
        }

        @Override
        public int run() {
            if (operations.isEmpty()) {
                return RESULT_FINISHED;
            }

            int result = operations.get(0).run();

            switch (result) {
                case RESULT_RETRY:
                    return RESULT_RETRY;
                case RESULT_CANCEL:
                    return RESULT_CANCEL;
                case RESULT_FINISHED:
                default:
                    operations.remove(0);
                    execute(this);
                    return RESULT_FINISHED;
            }
        }

    }

}

