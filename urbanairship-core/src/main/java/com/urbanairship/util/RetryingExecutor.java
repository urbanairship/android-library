/* Copyright Airship and Contributors */

package com.urbanairship.util;

import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;

import com.urbanairship.AirshipExecutors;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.Executor;

import androidx.annotation.NonNull;
import androidx.annotation.RestrictTo;

/**
 * Executor that supports retrying operations when
 * using {@link #execute(Operation)} to submit operations.
 *
 * @hide
 */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
public class RetryingExecutor implements Executor {

    private static final Result FINISHED_RESULT = new Result(Status.FINISHED, -1);
    private static final Result CANCEL_RESULT = new Result(Status.CANCEL, -1);

    /**
     * Initial retry backoff
     */
    public static final long INITIAL_BACKOFF_MILLIS = 30000; // 30 seconds

    /**
     * Max backoff
     */
    private static final long MAX_BACKOFF_MILLIS = 120000; // 2 minutes

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

    public static RetryingExecutor newSerialExecutor(Looper looper) {
        return new RetryingExecutor(new Handler(looper), AirshipExecutors.newSerialExecutor());
    }

    public static Result retryResult() {
        return new Result(Status.RETRY, -1);
    }

    public static Result retryResult(long nextBackOff) {
        return new Result(Status.RETRY, nextBackOff);
    }

    public static Result finishedResult() {
        return FINISHED_RESULT;
    }

    public static Result cancelResult() {
        return CANCEL_RESULT;
    }

    /**
     * Executes a runnable. The runnable will not be retried.
     *
     * @param runnable The runnable
     */
    @Override
    public void execute(@NonNull final Runnable runnable) {
        execute(() -> {
            runnable.run();
            return finishedResult();
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
     * @param nextBackOff The next backOff if retrying the operation.
     */
    public void execute(final @NonNull Operation operation, final long nextBackOff) {
        final Runnable executeRunnable = new Runnable() {
            @Override
            public void run() {
                synchronized (pendingRunnables) {
                    if (isPaused) {
                        pendingRunnables.add(this);
                        return;
                    }
                }

                Result result = operation.run();

                if (result.status == Status.RETRY) {
                    long backOff = result.nextBackOff >= 0 ? result.nextBackOff : nextBackOff;
                    scheduler.postAtTime(() -> {
                        execute(operation, calculateBackoff(backOff));
                    }, executor, SystemClock.uptimeMillis() + backOff);
                }
            }
        };

        executor.execute(executeRunnable);
    }

    private long calculateBackoff(long lastBackOff) {
        if (lastBackOff <= 0) {
            return INITIAL_BACKOFF_MILLIS;
        }
        return Math.min(lastBackOff * 2, MAX_BACKOFF_MILLIS);
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
         * @return The result.
         */
        @NonNull
        Result run();
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
        @NonNull
        public Result run() {
            if (operations.isEmpty()) {
                return finishedResult();
            }

            Result result = operations.get(0).run();

            if (result.status == Status.FINISHED) {
                operations.remove(0);
                execute(this);
            }

            return result;
        }
    }


    public enum Status {
        FINISHED,
        RETRY,
        CANCEL
    }

    public static class Result {

        private final Status status;

        // -1 means use current, otherwise use next
        private final long nextBackOff;

        private Result(Status status, long nextBackOff) {
            this.status = status;
            this.nextBackOff = nextBackOff;
        }
    }
}
