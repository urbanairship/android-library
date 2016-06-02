/* Copyright 2016 Urban Airship and Contributors */

package com.urbanairship;

import android.support.annotation.Nullable;

/**
 * A generic pending result.
 *
 * @param <T> Type of result.
 */
public class PendingResult<T> implements Cancelable {

    /**
     * Result callback interface.
     *
     * @param <T> The type of result.
     */
    public interface ResultCallback<T> {
        void onResult(@Nullable T result);
    }

    private boolean isCanceled;

    @Nullable
    private ResultCallback<T> callback;

    @Nullable
    private T result;


    public PendingResult(@Nullable ResultCallback<T> callback) {
        this.callback = callback;
    }

    /**
     * Cancels the pending result.
     */
    @Override
    public void cancel() {
        synchronized (this) {
            if (isCanceled()) {
                return;
            }

            onCancel();
            isCanceled = true;
        }
    }

    /**
     * Called when the PendingResult is canceled.
     */
    protected void onCancel() {

    }

    /**
     * Sets the pending result.
     *
     * @param result The pending result.
     */
    public void setResult(@Nullable T result) {
        synchronized (this) {
            if (isDone()) {
                return;
            }

            this.result = result;
            if (callback != null) {
                callback.onResult(result);
            }
        }
    }

    @Override
    public boolean isCanceled() {
        synchronized (this) {
            return isCanceled;
        }
    }

    @Override
    public boolean isDone() {
        synchronized (this) {
            return isCanceled || result != null;
        }
    }
}
