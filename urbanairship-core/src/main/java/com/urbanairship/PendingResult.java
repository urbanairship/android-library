/* Copyright Airship and Contributors */

package com.urbanairship;

import android.os.Looper;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RestrictTo;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * A pending result.
 *
 * @param <T> Type of result.
 */
public class PendingResult<T> implements Cancelable, Future<T> {

    private boolean isCanceled;
    private boolean resultSet;
    private boolean runCallbacks = true;

    @Nullable
    private T result;

    private final List<Cancelable> cancelables = new ArrayList<>();
    private final List<CancelableOperation> resultCallbacks = new ArrayList<>();

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

            runCallbacks = false;

            // Cancel any callbacks just in-case they are still pending execution
            for (Cancelable pendingCallback : resultCallbacks) {
                pendingCallback.cancel(mayInterruptIfRunning);
            }

            resultCallbacks.clear();

            if (isDone()) {
                return false;
            }

            isCanceled = true;

            this.notifyAll();

            for (Cancelable cancelable : cancelables) {
                cancelable.cancel(mayInterruptIfRunning);
            }

            cancelables.clear();

            return true;
        }
    }

    /**
     * Sets the pending result.
     *
     * @param result The pending result.
     * @hide
     */
    @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
    public void setResult(final @Nullable T result) {
        synchronized (this) {
            if (isDone()) {
                return;
            }

            this.result = result;
            this.resultSet = true;
            this.cancelables.clear();

            this.notifyAll();

            for (CancelableOperation callback : resultCallbacks) {
                callback.run();
            }
            resultCallbacks.clear();
        }
    }

    /**
     * Returns the result if set.
     *
     * @return The result if set, otherwise {@code null}.
     */
    @Nullable
    public T getResult() {
        synchronized (this) {
            return result;
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

    @Nullable
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

    @Nullable
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
     * @param cancelable The instance that implements the {@link Cancelable} interface.
     */
    @NonNull
    public PendingResult<T> addCancelable(@NonNull Cancelable cancelable) {
        synchronized (this) {
            if (isCancelled()) {
                cancelable.cancel();
            }

            if (!isDone()) {
                cancelables.add(cancelable);
            }
        }

        return this;
    }

    /**
     * Adds a result callback.
     *
     * @param resultCallback The result callback.
     * @return The pending result.
     */
    @NonNull
    public PendingResult<T> addResultCallback(@NonNull final ResultCallback<T> resultCallback) {
        return addResultCallback(Looper.myLooper(), resultCallback);
    }

    /**
     * Adds a result callback.
     *
     * @param looper The looper to run the callback on.
     * @param resultCallback The result callback.
     * @return The pending result.
     */
    @NonNull
    public PendingResult<T> addResultCallback(@Nullable Looper looper, @NonNull final ResultCallback<T> resultCallback) {
        synchronized (this) {
            if (isCancelled() || !runCallbacks) {
                return this;
            }

            CancelableOperation pendingCallback = new CancelableOperation(looper) {
                @Override
                protected void onRun() {
                    synchronized (PendingResult.this) {
                        if (runCallbacks) {
                            resultCallback.onResult(result);
                        }
                    }
                }
            };

            if (isDone()) {
                pendingCallback.run();
            }

            resultCallbacks.add(pendingCallback);
        }

        return this;
    }

}
