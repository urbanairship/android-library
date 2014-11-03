package com.urbanairship;/*
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

/**
 * A generic pending result.
 *
 * @param <T> Type of result.
 */
public interface PendingResult<T> {

    /**
     * Result callback interface.
     *
     * @param <T> The type of result.
     */
    public interface ResultCallback<T> {
        public void onResult(T result);
    }

    /**
     * Sets a callback to be called when a result is received.
     *
     * @param resultCallback The result callback.
     */
    public void onResult(ResultCallback<T> resultCallback);

    /**
     * Cancels the pending result.
     */
    public void cancel();

    /**
     * Returns if the request has been canceled or not.
     *
     * @return <code>true</code> if canceled, <code>false</code> otherwise.
     */
    public boolean isCanceled();

    /**
     * Returns the current state of the result.
     *
     * @return <code>true</code> if done or canceled, <code>false</code> otherwise.
     */
    public boolean isDone();

}
