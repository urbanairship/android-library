/*
Copyright 2009-2015 Urban Airship Inc. All rights reserved.

Redistribution and use in source and binary forms, with or without
modification, are permitted provided that the following conditions are met:

1. Redistributions of source code must retain the above copyright notice, this
list of conditions and the following disclaimer.

2. Redistributions in binary form must reproduce the above copyright notice,
this list of conditions and the following disclaimer in the documentation
and/or other materials provided with the distribution.

THIS SOFTWARE IS PROVIDED BY THE URBAN AIRSHIP INC ``AS IS'' AND ANY EXPRESS OR
IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF
MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO
EVENT SHALL URBAN AIRSHIP INC OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT,
INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING,
BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE,
DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF
LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE
OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF
ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

package com.urbanairship;

import android.os.Handler;
import android.os.Looper;

/**
 * A cancelable operation that executes its task on a specific looper.
 */
abstract class CancelableOperation implements Cancelable, Runnable {

    private boolean isFinished = false;
    private boolean isRunning = false;
    private boolean isCanceled = false;
    private Handler handler;

    private final Runnable internalRunnable;

    /**
     * CancelableOperation constructor.
     * @param looper A Looper object whose message queue will be used for the callback,
     * or null to make callbacks on the calling thread or main thread if the current thread
     * does not have a looper associated with it.
     */
    CancelableOperation(Looper looper) {
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
