/* Copyright 2017 Urban Airship and Contributors */

package com.urbanairship.wallet;

import android.os.Looper;

import com.urbanairship.CancelableOperation;

/**
 * {@link CancelableOperation} wrapper around {@link Callback}.
 */
class CancelableCallback extends CancelableOperation {

    private Callback callback;
    private int status;
    private Pass pass;

    /**
     * Callback constructor.
     *
     * @param callback The request callback.
     * @param looper A Looper object whose message queue will be used for the callback,
     * or null to make callbacks on the calling thread or main thread if the current thread
     * does not have a looper associated with it.
     */
    public CancelableCallback(Callback callback, Looper looper) {
        super(looper);
        this.callback = callback;
    }

    @Override
    protected void onRun() {
        if (callback != null) {
            if (pass != null) {
                callback.onResult(pass);
            } else {
                callback.onError(status);
            }
        }
    }

    @Override
    protected void onCancel() {
        this.callback = null;
        this.pass = null;
    }

    /**
     * Sets the pass request result.
     *
     * @param status The request response status.
     * @param pass The parsed response {@link Pass}.
     */
    void setResult(int status, Pass pass) {
        if (isCanceled()) {
            return;
        }

        this.status = status;
        this.pass = pass;
    }
}
