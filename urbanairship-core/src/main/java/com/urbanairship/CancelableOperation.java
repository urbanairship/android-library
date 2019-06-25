/* Copyright Airship and Contributors */

package com.urbanairship;

import android.os.Handler;
import android.os.Looper;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * A cancelable operation that executes its task on a specific looper.
 */
public abstract class CancelableOperation implements Cancelable, Runnable {

    private boolean isFinished = false;
    private boolean isRunning = false;
    private boolean isCanceled = false;
    private final Handler handler;
    private final Runnable internalRunnable;

    private final List<Cancelable> cancelables = new ArrayList<>();
    private final List<Runnable> runnables = new ArrayList<>();

    /**
     * CancelableOperation constructor.
     */
    public CancelableOperation() {
        this(null);
    }

    /**
     * CancelableOperation constructor.
     *
     * @param looper A Looper object whose message queue will be used for the callback,
     * or null to make callbacks on the calling thread or main thread if the current thread
     * does not have a looper associated with it.
     */
    public CancelableOperation(@Nullable Looper looper) {
        if (looper != null) {
            this.handler = new Handler(looper);
        } else {
            Looper myLooper = Looper.myLooper();
            this.handler = myLooper != null ? new Handler(myLooper) : new Handler(Looper.getMainLooper());
        }

        internalRunnable = new Runnable() {
            @Override
            public void run() {
                synchronized (CancelableOperation.this) {
                    if (isDone()) {
                        return;
                    }

                    onRun();
                    isFinished = true;

                    for (Runnable runnable : runnables) {
                        runnable.run();
                    }

                    cancelables.clear();
                    runnables.clear();
                }
            }
        };
    }

    @Override
    public final boolean cancel() {
        return cancel(false);
    }

    @Override
    public final boolean cancel(boolean mayInterruptIfRunning) {
        synchronized (this) {
            if (!isDone()) {
                isCanceled = true;
                handler.removeCallbacks(internalRunnable);
                handler.post(new Runnable() {
                    @Override
                    public void run() {
                        onCancel();
                    }
                });

                for (Cancelable cancelable : cancelables) {
                    cancelable.cancel(mayInterruptIfRunning);
                }

                cancelables.clear();
                runnables.clear();

                return true;
            }

            return false;
        }
    }

    @Override
    public final void run() {
        synchronized (this) {
            if (isDone() || isRunning) {
                return;
            }

            isRunning = true;
            handler.post(internalRunnable);
        }
    }

    @Override
    public final boolean isDone() {
        synchronized (this) {
            return isFinished || isCanceled;
        }
    }

    @Override
    public final boolean isCancelled() {
        synchronized (this) {
            return isCanceled;
        }
    }

    /**
     * Adds a runnable that will be called when operation is finished. If the operation is already
     * finished the runnable will be called immediately.
     *
     * @param runnable A runnable.
     */
    @NonNull
    public CancelableOperation addOnRun(@NonNull Runnable runnable) {
        synchronized (this) {
            if (isFinished) {
                runnable.run();
            } else {
                runnables.add(runnable);
            }
        }

        return this;
    }

    /**
     * Adds a {@link Cancelable} that will be called when operation is cancelled.  If the operation
     * is already canceled the operation will immediately be canceled.
     *
     * @param cancelable The instance that implements the {@link Cancelable} interface.
     */
    @NonNull
    public CancelableOperation addOnCancel(@NonNull Cancelable cancelable) {
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
     * Called on the handlers callback when the operation is canceled.
     */
    protected void onCancel() {
    }

    /**
     * Called on the handlers callback when the operation is running.
     */
    protected void onRun() {
    }

    /**
     * Gets the handler for the operation.
     *
     * @return The operation's handler.
     */
    @NonNull
    public Handler getHandler() {
        return handler;
    }

}
