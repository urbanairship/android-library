/*
Copyright 2009-2014 Urban Airship Inc. All rights reserved.

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

package com.urbanairship.location;

import android.location.Location;

import com.urbanairship.PendingResult;

/**
 * Base class for pending single location requests.
 */
abstract class PendingLocationResult implements PendingResult<Location> {

    private boolean isCanceled;
    private ResultCallback<Location> resultCallback;
    private Location result;

    @Override
    public synchronized void onResult(ResultCallback<Location> resultCallback) {

        if (isCanceled || this.resultCallback != null) {
            return;
        }

        this.resultCallback = resultCallback;

        if (result != null) {
            this.resultCallback.onResult(result);
        }
    }

    @Override
    public synchronized void cancel() {
        if (isCanceled()) {
            return;
        }

        onCancel();
        isCanceled = true;
    }

    /**
     * Sets the location result.
     *
     * @param result The location result.
     */
    synchronized void setResult(Location result) {
        if (isDone()) {
            return;
        }

        this.result = result;
        if (resultCallback != null) {
            resultCallback.onResult(result);
        }

    }


    /**
     * Returns if the request has been canceled or not.
     *
     * @return <code>true</code> if canceled, <code>false</code> otherwise.
     */
    public synchronized boolean isCanceled() {
        return isCanceled;
    }

    /**
     * Returns if the pending result is done or not.
     *
     * @return <code>true</code> if done or canceled, <code>false</code> otherwise.
     */
    public synchronized boolean isDone() {
        return isCanceled || result != null;
    }

    /**
     * Called when the request has been canceled.
     */
    protected abstract void onCancel();
}
