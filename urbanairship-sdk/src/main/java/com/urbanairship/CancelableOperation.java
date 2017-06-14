/* Copyright 2017 Urban Airship and Contributors */

package com.urbanairship;

import android.os.Handler;
import android.os.Looper;

/**
 * A cancelable operation that executes its task on a specific looper.
 */
public abstract class CancelableOperation implements Cancelable, Runnable {

    private boolean isFinished = false;
    private boolean isRunning = false;
    private boolean isCanceled = false;
    private final Handler handler;

    private final Runnable internalRunnable;

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
    public CancelableOperation(Looper looper) {
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
                }
            }
        };
    }


    @Override
    public final void cancel() {
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
            }
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
    public final boolean isCanceled() {
        synchronized (this) {
            return isCanceled;
        }
    }

    /**
     * Called on the handlers callback when the operation is canceled.
     */
    protected void onCancel() {}

    /**
     * Called on the handlers callback when the operation is running.
     */
    protected abstract void onRun();

}
