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

package com.urbanairship.location;

import android.app.PendingIntent;
import android.location.Location;

import com.urbanairship.PendingResult;

/**
 * The common interface for communicating with different location sources.
 */
interface LocationAdapter {
    /**
     * Requests a single location update.
     *
     * @param options The location request options.
     * @return PendingResult that can be used to cancel the request or set a listener for
     * when the result is available.
     */
    PendingResult<Location> requestSingleLocation(LocationRequestOptions options);

    /**
     * Cancels location updates.
     *
     * @param intent The pending intent used to start location updates.
     */
    void cancelLocationUpdates(PendingIntent intent);

    /**
     * Requests location updates.
     *
     * @param options The location request options.
     * @param intent The pending intent used to start location updates.
     */
    void requestLocationUpdates(LocationRequestOptions options, PendingIntent intent);

    /**
     * Connects the adapter.
     *
     * @return <code>true</code> if the adapter connected,
     * <code>false</code> otherwise.
     */
    boolean connect();

    /**
     * Disconnects the adapter.
     */
    void disconnect();
}
