/* Copyright 2017 Urban Airship and Contributors */

package com.urbanairship;

import android.os.Handler;
import android.os.Looper;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * A generic pending result.
 *
 * @param <T> Type of result.
 */
public class PendingResult<T> implements Cancelable, Future<T> {

    private final Looper looper;


    private boolean isCanceled;
    private boolean resultSet;

    @Nullable
    private ResultCallback<T> callback;

    @Nullable
    private T result;


    private List<Cancelable> cancellables = new ArrayList<>();

    /**
     * Creates a pending result.
     */
    public PendingResult() {
        this.callback = null;
        this.looper = null;
    }

    /**
     * Creates a pending result with a callback.
     *
     * @param callback The callback.
     */
    public PendingResult(@Nullable ResultCallback<T> callback) {
        this.callback = callback;
        this.looper = null;
    }

    /**
     * Creates a pending result with a callback and specified looper.
     *
     * @param callback The callback.
     * @param looper The looper to run the callback on. If null, the callback is run on whatever
     * thread that sets the result.
     */
    public PendingResult(@Nullable ResultCallback<T> callback, Looper looper) {
        this.callback = callback;
        this.looper = looper;
    }

    @Override
    public final boolean cancel() {
        return cancel(false);
    }

    @Override
    public boolean cancel(boolean mayInterruptIfRunning) {
        synchronized (this) {
            if (isCancelled()) {
                return true;
            }

            // Clear the callback just in-case it's still pending execution
            callback = null;

            if (isDone()) {
                return false;
            }

            isCanceled = true;

            this.notifyAll();

            onCancel(mayInterruptIfRunning);

            for (Cancelable cancelable : cancellables) {
                cancelable.cancel(mayInterruptIfRunning);
            }
            cancellables.clear();

            return true;
        }
    }

    /**
     * Called when the PendingResult is canceled.
     *
     * @param mayInterruptIfRunning {@code true} if the thread executing this task should be interrupted; otherwise, in-progress tasks are allowed to complete.
     * @return {@code false} if the cancelable was able to be cancelled, otherwise {@code true}.
     */
    protected boolean onCancel(boolean mayInterruptIfRunning) {
        return true;
    }

    /**
     * Sets the pending result.
     *
     * @param result The pending result.
     */
    public void setResult(final @Nullable T result) {

        synchronized (this) {
            if (isDone()) {
                return;
            }

            this.result = result;
            this.resultSet = true;
            this.cancellables.clear();

            this.notifyAll();

            Runnable runnable = new Runnable() {
                @Override
                public void run() {
                    synchronized (PendingResult.this) {
                        if (isCancelled() && callback == null) {
                            return;
                        }

                        callback.onResult(result);
                        callback = null;
                    }
                }
            };

            // If we have a looper post the runnable before notifying the result
            if (looper != null) {
                new Handler(looper).post(runnable);
            } else {
                runnable.run();
            }
        }
    }


    @Override
    public boolean isCancelled() {
        synchronized (this) {
            return isCanceled;
        }
    }


    @Override
    public boolean isDone() {
        synchronized (this) {
            return isCanceled || resultSet;
        }
    }

    @Override
    public T get() throws InterruptedException, ExecutionException {
        synchronized (this) {
            if (isDone()) {
                return result;
            }
            this.wait();
            return result;
        }
    }

    @Override
    public T get(long l, @NonNull TimeUnit timeUnit) throws InterruptedException, ExecutionException, TimeoutException {
        synchronized (this) {
            if (isDone()) {
                return result;
            }

            this.wait(timeUnit.toMillis(l));

            return result;
        }
    }

    /**
     * Adds a {@link Cancelable} that will be called when
     * the pending result is canceled. If the pending result is already canceled the operation
     * will immediately be canceled.
     *
     * @param cancelable The instance that imeplements the {@link Cancelable} interface.
     */
    public void addCancelable(@NonNull Cancelable cancelable) {
        synchronized (this) {
            if (isCancelled()) {
                cancelable.cancel(true);
            }

            if (!isDone()) {
                cancellables.add(cancelable);
            }
        }
    }
}
