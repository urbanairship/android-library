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